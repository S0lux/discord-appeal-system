package com.sopuro.appeal_system.commands.panel;

import com.sopuro.appeal_system.commands.SlashCommand;
import com.sopuro.appeal_system.components.messages.GenericSuccessFollowUp;
import com.sopuro.appeal_system.configs.AppealSystemConfig;
import com.sopuro.appeal_system.dtos.GameConfigDto;
import com.sopuro.appeal_system.exceptions.appeal.MissingGuildContextException;
import com.sopuro.appeal_system.shared.enums.AppealRole;
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
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PanelCommandHandler implements SlashCommand {
    public static final String CROSSROADS_DISCORD_BTN_PREFIX = "crossroads:discord_btn_";
    public static final String CROSSROADS_IN_GAME_BTN_PREFIX = "crossroads:in-game_btn_";
    private final GatewayDiscordClient gatewayDiscordClient;
    private final AppealSystemConfig appealSystemConfig;

    @Override
    public String getName() {
        return "panel";
    }

    @Override
    public List<AppealRole> allowedRoles() {
        return List.of(AppealRole.OVERSEER);
    }

    @Override
    public Mono<Void> preCondition(ChatInputInteractionEvent event) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> handle(ChatInputInteractionEvent event) {
        ApplicationCommandInteractionOption option = event.getOptions().getFirst();

        if (option == null)
            return event.editReply("Please provide a valid option for the panel command.").then();

        if (option.getName().equals("create")) return handleCreatePanel(event);

        return event.editReply("Unknown option: " + option.getName()).then();
    }

    private Mono<Void> handleCreatePanel(ChatInputInteractionEvent event) {
        Snowflake channelId = event.getInteraction().getChannelId();
        Optional<Snowflake> guildId = event.getInteraction().getGuildId();
        if (guildId.isEmpty()) return Mono.error(new MissingGuildContextException());

        PanelType panelType = event.getOption("create")
                .flatMap(option -> option.getOption("type"))
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .map(String::toUpperCase)
                .map(PanelType::valueOf)
                .orElseThrow();

        MessageCreateSpec panelMessageSpec =
                switch (panelType) {
                    case PanelType.CROSSROADS ->
                        MessageCreateSpec.create()
                                .withFlags(Message.Flag.IS_COMPONENTS_V2)
                                .withComponents(
                                        createCrossroadsPanel(guildId.get().asString()));
                };

        return gatewayDiscordClient
                .getChannelById(channelId)
                .cast(TextChannel.class)
                .flatMap(textChannel -> textChannel.createMessage(panelMessageSpec))
                .then(event.createFollowup(GenericSuccessFollowUp.create("Panel has been created")))
                .onErrorResume(error -> event.editReply("Failed to create panel: " + error.getMessage()))
                .then();
    }

    private Container createCrossroadsPanel(String serverId) {
        GameConfigDto gameConfig = appealSystemConfig.getGames().stream()
                .filter(gameConfigDto -> gameConfigDto.appealServerId().equals(serverId))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException("This server is not configured to be an APPEAL server."));

        Section textSection = Section.of(Thumbnail.of(UnfurledMediaItem.of(gameConfig.image())));

        // A section can only have one thumbnail and at most three text displays.
        for (int i = 0; i < (Math.min(gameConfig.crossroadDescription().size(), 3)); i++) {
            textSection = textSection.withAddedComponent(
                    TextDisplay.of(gameConfig.crossroadDescription().get(i)));
        }

        return Container.of(
                textSection,
                Separator.of(),
                ActionRow.of(
                        Button.secondary(CROSSROADS_DISCORD_BTN_PREFIX + gameConfig.normalizedName(), "Appeal Discord"),
                        Button.secondary(
                                CROSSROADS_IN_GAME_BTN_PREFIX + gameConfig.normalizedName(), "Appeal In-Game")));
    }
}
