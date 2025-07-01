package com.sopuro.appeal_system.commands.panel;

import com.sopuro.appeal_system.commands.SlashCommand;
import com.sopuro.appeal_system.configs.AppealSystemConfig;
import com.sopuro.appeal_system.dtos.GameConfigDto;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.component.*;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.MessageCreateSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PanelCommandHandler implements SlashCommand {
    private final GatewayDiscordClient gatewayDiscordClient;
    private final AppealSystemConfig appealSystemConfig;

    public static final String CROSSROADS_DISCORD_BTN_PREFIX = "crossroads:discord_btn_";
    public static final String CROSSROADS_IN_GAME_BTN_PREFIX = "crossroads:in-game_btn_";

    @Override
    public String getName() {
        return "panel";
    }

    @Override
    public List<String> getRoles() {
        return List.of("OVERSEER");
    }

    @Override
    public Mono<Void> handle(ChatInputInteractionEvent event) {
        ApplicationCommandInteractionOption option = event.getOptions().getFirst();

        if (option == null)
            return event.reply("Please provide a valid option for the panel command.").withEphemeral(true);

        if (option.getName().equals("create"))
            return handleCreatePanel(event);

        return event.reply("Unknown option: " + option.getName()).withEphemeral(true);
    }

    private Mono<Void> handleCreatePanel(ChatInputInteractionEvent event) {
        Snowflake channelId = event.getInteraction().getChannelId();
        Snowflake guildId = event.getInteraction().getGuildId()
                .orElseThrow(() -> new IllegalStateException("Guild ID is not present in the interaction"));

        PanelType panelType = event.getOption("create")
                .flatMap(option -> option.getOption("type"))
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .map(String::toUpperCase)
                .map(PanelType::valueOf)
                .orElseThrow();

        MessageCreateSpec panelMessageSpec = switch (panelType) {
            case PanelType.CROSSROADS -> MessageCreateSpec
                    .create()
                    .withFlags(Message.Flag.IS_COMPONENTS_V2)
                    .withComponents(createCrossroadsPanel(guildId.asString()));
        };

        return event.deferReply().withEphemeral(true)
                .then(gatewayDiscordClient.getChannelById(channelId)
                        .cast(TextChannel.class)
                        .flatMap(textChannel -> textChannel.createMessage(panelMessageSpec))
                        .then(event.editReply("Panel created successfully!"))
                        .onErrorResume(error -> event.editReply("Failed to create panel: " + error.getMessage())))
                .then();
    }

    private Container createCrossroadsPanel(String serverId) {
        GameConfigDto gameConfig = appealSystemConfig.getGames().stream()
                .filter(gameConfigDto -> gameConfigDto.appealServerId().equals(serverId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("This server is not configured to be an APPEAL server."));

        Section textSection = Section.of(Thumbnail.of(UnfurledMediaItem.of(gameConfig.image())));

        // A section can only have one thumbnail and at most three text displays.
        for (int i = 0; i < (Math.min(gameConfig.crossroadDescription().size(), 3)); i++) {
            textSection = textSection.withAddedComponent(
                    TextDisplay.of(gameConfig.crossroadDescription().get(i))
            );
        }

        return Container.of(
                textSection, Separator.of(),
                ActionRow.of(
                        Button.secondary(CROSSROADS_DISCORD_BTN_PREFIX + gameConfig.normalizedName(), "Appeal Discord"),
                        Button.secondary(CROSSROADS_IN_GAME_BTN_PREFIX + gameConfig.normalizedName(), "Appeal In-Game")
                )
        );
    }
}
