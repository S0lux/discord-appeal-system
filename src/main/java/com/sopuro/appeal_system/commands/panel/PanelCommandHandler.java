package com.sopuro.appeal_system.commands.panel;

import com.sopuro.appeal_system.commands.SlashCommand;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class PanelCommandHandler implements SlashCommand {
    private final GatewayDiscordClient gatewayDiscordClient;

    public static final String CROSSROADS_DISCORD_BTN = "crossroads:discord_btn";
    public static final String CROSSROADS_IN_GAME_BTN = "crossroads:in-game_btn";

    @Override
    public String getName() {
        return "panel";
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
        Snowflake invokerId = event.getUser().getId();
        Snowflake channelId = event.getInteraction().getChannelId();
        String panelType = event.getOption("create")
                .flatMap(option -> option.getOption("type"))
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .orElseThrow();

        log.info("User {} requested to create a panel type: {}", invokerId.asString(), panelType);

        return event.deferReply().withEphemeral(true)
                .then(gatewayDiscordClient.getChannelById(channelId)
                        .cast(TextChannel.class)
                        .flatMap(textChannel ->
                                textChannel
                                        .createMessage(
                                                MessageCreateSpec
                                                        .create()
                                                        .withFlags(Message.Flag.IS_COMPONENTS_V2)
                                                        .withComponents(createCrossroadsPanel()))
                                        .doOnSuccess(ignore ->
                                                log.info("Panel created successfully in channel: {}", textChannel.getId().asString())
                                        ))
                        .then(event.editReply("Panel created successfully!"))
                        .then());
    }

    private Container createCrossroadsPanel() {
        return Container.of(
                Section.of(
                        Thumbnail.of(UnfurledMediaItem.of("https://cdn.discordapp.com/icons/1040045289794441336/f6d2863674b2df0d0669a252f061cd78.png?size=256&quality=lossless")),
                        TextDisplay.of("# :bank:  EC | Appeal Hub  :bank:"),
                        TextDisplay.of("Welcome to the EC Appeal Hub! This is your opportunity to reconnect with our community by submitting an appeal and addressing past offenses."),
                        TextDisplay.of("Please note that appeals that appear low-effort will be rejected.")
                ),
                Separator.of(),
                ActionRow.of(
                        Button.secondary(CROSSROADS_DISCORD_BTN, "Appeal Discord"),
                        Button.secondary(CROSSROADS_IN_GAME_BTN, "Appeal In-Game")
                )
        );
    }
}
