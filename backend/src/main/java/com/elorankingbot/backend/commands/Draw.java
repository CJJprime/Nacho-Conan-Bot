package com.elorankingbot.backend.commands;

import com.elorankingbot.backend.tools.Buttons;
import com.elorankingbot.backend.model.ChallengeModel;
import com.elorankingbot.backend.model.Match;
import com.elorankingbot.backend.service.DiscordBotService;
import com.elorankingbot.backend.service.EloRankingService;
import com.elorankingbot.backend.tools.MessageUpdater;
import com.elorankingbot.backend.timedtask.TimedTask;
import com.elorankingbot.backend.timedtask.TimedTaskQueue;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.object.component.ActionRow;

public class Draw extends ButtonCommandRelatedToChallenge {

	public Draw(ButtonInteractionEvent event, EloRankingService service, DiscordBotService bot,
				TimedTaskQueue queue, GatewayDiscordClient client) {
		super(event, service, bot, queue, client);
	}

	public void execute() {
		ChallengeModel.ReportIntegrity reportIntegrity;
		if (isChallengerCommand) reportIntegrity = challenge.setChallengerReported(ChallengeModel.ReportStatus.DRAW);
		else reportIntegrity = challenge.setAcceptorReported(ChallengeModel.ReportStatus.DRAW);

		if (reportIntegrity == ChallengeModel.ReportIntegrity.FIRST_TO_REPORT) processFirstToReport();
		if (reportIntegrity == ChallengeModel.ReportIntegrity.HARMONY) processHarmony();
		if (reportIntegrity == ChallengeModel.ReportIntegrity.CONFLICT) processConflict();
		event.acknowledge().subscribe();
	}

	private void processFirstToReport() {
		service.saveChallenge(challenge);

		new MessageUpdater(parentMessage)
				.makeAllNotBold()
				.addLine("You reported a draw :left_right_arrow:. I'll let you know when your opponent reports.")
				.update()
				.withComponents(none).subscribe();
		new MessageUpdater(targetMessage)
				.addLine("Your opponent reported a draw :left_right_arrow:.")
				.update().subscribe();
	}

	private void processHarmony() {
		Match match = new Match(guildId, challenge.getChallengerId(), challenge.getAcceptorId(), true);
		double[] eloResults = service.updateRatings(match);// TODO transaction machen?
		service.saveMatch(match);
		service.deleteChallenge(challenge);

		new MessageUpdater(parentMessage)
				.makeAllNotBold()
				.addLine("You reported a draw :left_right_arrow:. The match has been resolved:")
				.addLine(String.format("Your rating went from %s to %s.",
						service.formatRating(eloResults[0]), service.formatRating(eloResults[2])))
				.makeAllItalic()
				.update()
				.withComponents(none).subscribe();
		new MessageUpdater(targetMessage)
				.makeAllNotBold()
				.addLine("Your opponent reported a draw :left_right_arrow:. The match has been resolved:")
				.addLine(String.format("Your rating went from %s to %s.",
						service.formatRating(eloResults[1]), service.formatRating(eloResults[3])))
				.makeAllItalic()
				.update()
				.withComponents(none).subscribe();

		bot.postToResultChannel(game, match);

		queue.addTimedTask(TimedTask.TimedTaskType.MATCH_SUMMARIZE, game.getMessageCleanupTime(),
				parentMessage.getId().asLong(), parentMessage.getChannelId().asLong(), match);
		queue.addTimedTask(TimedTask.TimedTaskType.MATCH_SUMMARIZE, game.getMessageCleanupTime(),
				targetMessage.getId().asLong(), targetMessage.getChannelId().asLong(), match);
	}

	private void processConflict() {
		service.saveChallenge(challenge);

		new MessageUpdater(parentMessage)
				.makeAllNotBold()
				.addLine("You reported a draw :left_right_arrow:.")
				.addLine("Your report and that of your opponent is in conflict. You can call for a redo of the reporting, " +
						"and/or call for a cancel, or file a dispute.")
				.makeLastLineBold()
				.update()
				.withComponents(ActionRow.of(
						Buttons.redo(targetMessage.getChannelId().asLong()),
						Buttons.cancelOnConflict(targetMessage.getChannelId().asLong()),
						Buttons.redoOrCancelOnConflict(targetMessage.getChannelId().asLong()),
						Buttons.dispute(targetMessage.getChannelId().asLong()))).subscribe();
		new MessageUpdater(targetMessage)
				.addLine("Your opponent reported a draw :left_right_arrow:.")
				.addLine("Your report and that of your opponent is in conflict. You can call for a redo of the reporting, " +
						"and/or call for a cancel, or file a dispute.")
				.makeLastLineBold()
				.update()
				.withComponents(ActionRow.of(
						Buttons.redo(targetMessage.getChannelId().asLong()),
						Buttons.cancelOnConflict(targetMessage.getChannelId().asLong()),
						Buttons.redoOrCancelOnConflict(targetMessage.getChannelId().asLong()),
						Buttons.dispute(targetMessage.getChannelId().asLong()))).subscribe();
	}
}
