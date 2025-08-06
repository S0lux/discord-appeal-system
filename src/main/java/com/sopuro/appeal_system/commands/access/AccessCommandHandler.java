package com.sopuro.appeal_system.commands.access;

import com.sopuro.appeal_system.commands.SlashCommand;
import com.sopuro.appeal_system.components.messages.AccessCodeDetailsMessage;
import com.sopuro.appeal_system.components.messages.CaseAccessDetailsMessage;
import com.sopuro.appeal_system.entities.CaseEntity;
import com.sopuro.appeal_system.exceptions.AppealException;
import com.sopuro.appeal_system.exceptions.appeal.CaseNotFoundException;
import com.sopuro.appeal_system.repositories.CaseRepository;
import com.sopuro.appeal_system.shared.enums.AppealRole;
import com.sopuro.appeal_system.shared.utils.EncryptionHelper;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.User;
import discord4j.core.spec.MessageCreateSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccessCommandHandler implements SlashCommand {
    private final CaseRepository caseRepository;
    private final GatewayDiscordClient gateway;

    @Value("${appeal-system.front-end.domain}")
    private String accessDomain;

    @Override
    public String getName() {
        return "code";
    }

    @Override
    public List<AppealRole> allowedRoles() {
        return List.of(AppealRole.OVERSEER, AppealRole.OVERSEER);
    }

    @Override
    public Mono<Void> preCondition(ChatInputInteractionEvent event) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> handle(ChatInputInteractionEvent event) {
        String subCommand = getSubcommand(event);
        return switch (subCommand) {
            case "generate" -> handleGenerateCommand(event);
            case "info" -> handleInfoCommand(event);
            default -> throw new IllegalStateException("Unexpected value: " + subCommand);
        };
    }

    private String getSubcommand(ChatInputInteractionEvent event) {
        return event.getOptions().getFirst().getName();
    }

    private Mono<Void> handleGenerateCommand(ChatInputInteractionEvent event) {
        ApplicationCommandInteractionOption option =
                event.getOption("generate").orElseThrow(() -> new IllegalStateException("This option is expected"));

        try {
            UUID caseId = UUID.fromString(getFieldFromCommandOption(option, "case_id"));
            return getCaseById(caseId)
                    .flatMap(caseEntity -> sendAccessDetailsToDM(event.getUser().getId(), caseEntity))
                    .then(Mono.defer(
                            () -> event.editReply("A message containing access details has been delivered to your DM")))
                    .then();
        } catch (IllegalArgumentException ex) {
            return Mono.error(new AppealException("This is not a valid case ID"));
        }
    }

    private Mono<Void> handleInfoCommand(ChatInputInteractionEvent event) {
        ApplicationCommandInteractionOption option =
                event.getOption("info").orElseThrow(() -> new IllegalStateException("This option is expected"));

        try {
            String accessCode = getFieldFromCommandOption(option, "access_code");
            EncryptionHelper.CaseAccessDetails details = EncryptionHelper.decryptCaseAccessCode(accessCode);

            if (details == null)
                return event.editReply("Not a valid access code").then();
            else
                return event.deleteReply().then(Mono.defer(() -> event.getInteraction()
                        .getChannel()
                        .flatMap(channel -> channel.createMessage(AccessCodeDetailsMessage.create(details)))
                        .then()));
        } catch (IllegalArgumentException ex) {
            return Mono.error(new AppealException("This is not a valid case ID"));
        }
    }

    private String getFieldFromCommandOption(ApplicationCommandInteractionOption option, String field)
            throws IllegalArgumentException {
        return option.getOption(field)
                .map(caseIdOption -> caseIdOption
                        .getValue()
                        .orElseThrow(() -> new IllegalArgumentException("Required option: " + field)))
                .map(ApplicationCommandInteractionOptionValue::asString)
                .orElseThrow(() -> new IllegalArgumentException("Required option: " + field));
    }

    private Mono<CaseEntity> getCaseById(UUID caseId) throws CaseNotFoundException {
        return Mono.fromCallable(() -> caseRepository.findById(caseId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(caseOptional -> caseOptional.map(Mono::just).orElse(Mono.error(CaseNotFoundException::new)));
    }

    private Mono<Void> sendAccessDetailsToDM(Snowflake userId, CaseEntity caseEntity) {
        String accessCode =
                EncryptionHelper.generateCaseAccessCode(caseEntity.getId().toString(), userId.asString());
        MessageCreateSpec caseAccessDetails = CaseAccessDetailsMessage.create(caseEntity, accessDomain, accessCode);

        return gateway.getUserById(userId)
                .flatMap(User::getPrivateChannel)
                .flatMap(channel -> channel.createMessage(caseAccessDetails))
                .then();
    }
}
