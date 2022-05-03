package com.elorankingbot.backend.commands.player;

import com.elorankingbot.backend.command.PlayerCommand;
import com.elorankingbot.backend.commands.SlashCommand;
import com.elorankingbot.backend.model.Game;
import com.elorankingbot.backend.model.MatchResult;
import com.elorankingbot.backend.model.Player;
import com.elorankingbot.backend.model.RankingsExcerpt;
import com.elorankingbot.backend.service.EmbedBuilder;
import com.elorankingbot.backend.service.Services;
import com.elorankingbot.backend.timedtask.DurationParser;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.User;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static discord4j.core.object.command.ApplicationCommandOption.Type.USER;

@PlayerCommand
public class PlayerInfo extends SlashCommand {

	private Player targetPlayer;
	private boolean isSelfInfo;

	public PlayerInfo(ChatInputInteractionEvent event, Services services) {
		super(event, services);
	}

	public static ApplicationCommandRequest getRequest() {
		return ApplicationCommandRequest.builder()
				.name(PlayerInfo.class.getSimpleName().toLowerCase())
				.description(getShortDescription())
				.addOption(ApplicationCommandOptionData.builder()
						.name("player")
						.description("The player to get information on. Omit to get information on yourself.")
						.type(USER.getValue())
						.required(false)
						.build())
				.build();
	}

	public static String getShortDescription() {
		return "Get information on a player.";
	}

	public static String getLongDescription() {
		return getShortDescription() + "\n" +
				"`Optional:` `player` The player to get information about. If this is omitted, you will get information " +
				"about yourself, visible to only yourself. Otherwise you will get openly visible information.";
	}

	public void execute() {
		User targetUser;
		if (event.getOption("player").isEmpty()) {
			targetUser = event.getInteraction().getUser();
			isSelfInfo = true;
		} else {
			targetUser = event.getOption("player").get().getValue().get().asUser().block();
			isSelfInfo = false;
		}

		if (targetUser.isBot()) {
			event.reply("Bots are not players.").subscribe();
			return;
		}
		Optional<Player> maybeTargetPlayer = dbService.findPlayerByGuildIdAndUserId(guildId, targetUser.getId().asLong());
		if (maybeTargetPlayer.isEmpty()) {
			event.reply("No player data found. That user has not yet used the bot.").subscribe();
			return;
		}
		targetPlayer = maybeTargetPlayer.get();

		List<EmbedCreateSpec> embeds = new ArrayList<>();
		for (String gameName : targetPlayer.getGameNameToPlayerGameStats().keySet()) {
			Game game = server.getGame(gameName);
			RankingsExcerpt rankingsExcerpt = dbService.getRankingsExcerptForPlayer(game, targetPlayer);
			embeds.add(EmbedBuilder.createRankingsEmbed(rankingsExcerpt));
			embeds.add(EmbedBuilder.createMatchHistoryEmbed(targetPlayer, getMatchHistory(game)));
		}
		String banString = createBanString();

		if (embeds.isEmpty()) {
			event.reply(banString + "No match data found. That user has likely not yet played a match.")
					.withEphemeral(isSelfInfo).subscribe();
			return;
		}

		var reply = event.reply();
		if (targetPlayer.isBanned()) reply = reply.withContent(banString);
		reply.withEmbeds(embeds).withEphemeral(isSelfInfo).subscribe();
	}

	private String createBanString() {
		String banString = "";
		if (targetPlayer.isBanned()) {
			if (targetPlayer.isPermaBanned()) {
				banString = String.format("**%s banned permanently, or until unbanned.**\n",
						isSelfInfo ? "You are" : "This player is");
			} else {
				int stillBannedMinutes = timedTaskQueue.getRemainingDuration(targetPlayer.getUnbanAtTimeSlot());
				banString = String.format("**%s still banned for %s.**\n",
						isSelfInfo ? "You are" : "This player is",
						DurationParser.minutesToString(stillBannedMinutes));
			}
		}
		return banString;
	}

	private List<Optional<MatchResult>> getMatchHistory(Game game) {
		return targetPlayer.getOrCreateGameStats(game).getMatchHistory().stream()
				.map(dbService::findMatchResult)
				.toList();
	}
}