package com.elorankingbot.backend.commands.timed;

import com.elorankingbot.backend.model.Game;
import com.elorankingbot.backend.service.DiscordBotService;
import com.elorankingbot.backend.service.EloRankingService;
import com.elorankingbot.backend.timedtask.TimedTask;
import com.elorankingbot.backend.timedtask.TimedTaskQueue;
import com.elorankingbot.backend.tools.MessageUpdater;
import discord4j.core.GatewayDiscordClient;

import java.util.Optional;

public class DecayOpenChallenge extends TimedCommand {

	public DecayOpenChallenge(EloRankingService service, DiscordBotService bot, GatewayDiscordClient client, TimedTaskQueue queue,
							  long challengeId, int time) {
		super(service, bot, queue, client, challengeId, time);
	}

	public void execute() {
		if (challenge == null) return;
		if (challenge.isAccepted()) return;

		service.deleteChallengeById(relationId);
		Optional<Game> maybeGame = service.findGameByGuildId(challenge.getGuildId());
		if (maybeGame.isEmpty()) return;

		new MessageUpdater(challenge.getChallengerMessageId(), challenge.getChallengerChannelId(), client)
				.addLine(String.format("This challenge has expired after not getting accepted within %s minutes.", time))
				.makeAllItalic()
				.update().subscribe();
		new MessageUpdater(challenge.getAcceptorMessageId(), challenge.getAcceptorChannelId(), client)
				.makeAllNotBold()
				.addLine(String.format("This challenge has expired after not getting accepted within %s minutes.", time))
				.makeAllItalic()
				.update()
				.withComponents(none).subscribe();

		int timer = service.findGameByGuildId(challenge.getGuildId()).get().getMessageCleanupTime();
		queue.addTimedTask(TimedTask.TimedTaskType.MESSAGE_DELETE, timer,
				challenge.getChallengerMessageId(), challenge.getChallengerChannelId(), null);
		queue.addTimedTask(TimedTask.TimedTaskType.MESSAGE_DELETE, timer,
				challenge.getAcceptorMessageId(), challenge.getAcceptorChannelId(), null);
	}
}
