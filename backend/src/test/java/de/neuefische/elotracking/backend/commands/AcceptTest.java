package de.neuefische.elotracking.backend.commands;

import de.neuefische.elotracking.backend.model.ChallengeModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static de.neuefische.elotracking.backend.commands.SnowflakeTestFactory.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AcceptTest extends CommandTest {

   private List<ChallengeModel> challenges = ChallengeModelTestFactory.createList();

    @BeforeEach
    void initService() {
        when(service.findGameByChannelId(CHANNEL_ID)).thenReturn(GameTestFactory.create());
        when(service.findAllChallengesByAcceptorIdAndChannelId(ACCEPTOR_ID, CHANNEL_ID)).thenReturn(challenges);
    }

    @ParameterizedTest
    @ValueSource(strings = {"!accept @%s", "!accept"})
    @DisplayName("No open challenge should not call service")
    void noOpenChallenge(String text) {
        text = String.format(text, CHALLENGER_ID);
        msg = MessageTestFactory.createMock(text, ACCEPTOR);
        command = new Accept(msg, service, bot, queue);

        command.execute();

        verify(service, never()).addNewPlayerIfPlayerNotPresent(any(), any());
    }

    @Test
    @DisplayName("Mention present but open challenge from a different player should not call service")
    void openChallengeFromDifferentPlayer() {
        String text = String.format("!accept @%s", CHALLENGER_ID);
        ChallengeModel challengeFromDifferentPlayer = ChallengeModelTestFactory.create();
        challengeFromDifferentPlayer.setChallengerId(SnowflakeTestFactory.createId());
        challenges.add(challengeFromDifferentPlayer);
        msg = MessageTestFactory.createMock(text, ACCEPTOR);
        command = new Accept(msg, service, bot, queue);

        command.execute();

        verify(service, never()).addNewPlayerIfPlayerNotPresent(any(), any());
    }

    @Test
    @DisplayName("No mention but two open challenges should not call service")
    void noMentionTwoChallenges() {
        String text = "!accept";
        challenges.add(ChallengeModelTestFactory.create());
        ChallengeModel challenge2 = ChallengeModelTestFactory.create();
        challenge2.setChallengerId(SnowflakeTestFactory.createId());
        challenges.add(challenge2);
        msg = MessageTestFactory.createMock(text, ACCEPTOR);
        command = new Accept(msg, service, bot, queue);

        command.execute();

        verify(service, never()).addNewPlayerIfPlayerNotPresent(any(), any());
    }

    @Test
    @DisplayName("One open challenge and no mention should call service")
    void oneChallengeNoMention() {
        String text = "!accept";
        challenges.add(ChallengeModelTestFactory.create());
        msg = MessageTestFactory.createMock(text, ACCEPTOR);
        command = new Accept(msg, service, bot, queue);

        command.execute();

        verify(service).saveChallenge(any());
    }

    @Test
    @DisplayName("Open challenges and a matching mention should call service")
    void openChallengesMatchingMention() {
        String text = String.format("!accept @%s", CHALLENGER_ID);
        challenges.add(ChallengeModelTestFactory.create());
        ChallengeModel challenge2 = ChallengeModelTestFactory.create();
        challenge2.setChallengerId(SnowflakeTestFactory.createId());
        challenges.add(challenge2);
        msg = MessageTestFactory.createMock(text, ACCEPTOR);
        command = new Accept(msg, service, bot, queue);

        command.execute();

        verify(service).saveChallenge(any());
    }
}