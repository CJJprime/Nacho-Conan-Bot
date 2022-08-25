package com.elorankingbot.backend.service;

import com.elorankingbot.backend.model.*;
import com.elorankingbot.backend.timedtask.TimedTaskQueue;
import discord4j.common.util.Snowflake;
import discord4j.core.object.PermissionOverwrite;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.Category;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.TextChannelCreateMono;
import discord4j.core.spec.TextChannelEditMono;
import discord4j.discordjson.possible.Possible;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.elorankingbot.backend.timedtask.TimedTask.TimedTaskType.CHANNEL_DELETE;

@Component
public class ChannelManager {

	private final DBService dbService;
	private final DiscordBotService bot;
	private final TimedTaskQueue timedTaskQueue;

	public ChannelManager(Services services) {
		this.bot = services.bot;
		this.dbService = services.dbService;
		this.timedTaskQueue = services.timedTaskQueue;
	}

	public Message postToResultChannel(MatchResult matchResult) {
		Game game = matchResult.getGame();
		TextChannel resultChannel = getOrCreateResultChannel(game);
		return resultChannel.createMessage(EmbedBuilder.createMatchResultEmbed(matchResult)).block();
	}

	public TextChannel getOrCreateResultChannel(Game game) {
		try {
			return (TextChannel) bot.getChannelById(game.getResultChannelId()).block();
		} catch (ClientException e) {// TODO funktioniert das ueberhaupt? testen!
			Guild guild = bot.getGuildById(game.getGuildId()).block();
			TextChannel resultChannel = guild.createTextChannel(String.format("%s match results", game.getName()))
					.withPermissionOverwrites(onlyBotCanSend(game.getServer()))
					.block();
			game.setResultChannelId(resultChannel.getId().asLong());
			dbService.saveServer(game.getServer());
			return resultChannel;
		}
	}

	private List<PermissionOverwrite> onlyBotCanSend(Server server) {
		return List.of(PermissionOverwrite.forRole(
						Snowflake.of(server.getEveryoneId()),
						PermissionSet.none(),
						PermissionSet.of(Permission.SEND_MESSAGES)),
				PermissionOverwrite.forMember(
						Snowflake.of(bot.botId),
						PermissionSet.of(Permission.SEND_MESSAGES),
						PermissionSet.none()));
	}

	public TextChannelCreateMono createDisputeChannel(Match match) {
		Server server = match.getServer();
		List<PermissionOverwrite> permissionOverwrites = excludePublic(server);
		match.getPlayers().forEach(player -> permissionOverwrites.add(allowPlayerView(player)));
		Category disputeCategory = getOrCreateDisputeCategory(server);
		return bot.getGuildById(match.getGame().getGuildId()).block()
				.createTextChannel(createMatchChannelName(match.getTeams()))
				.withParentId(disputeCategory.getId())
				.withPermissionOverwrites(permissionOverwrites);
	}

	public TextChannelCreateMono createMatchChannel(Match match) {
		Server server = match.getServer();
		List<PermissionOverwrite> permissionOverwrites = excludePublic(server);
		match.getPlayers().forEach(player -> permissionOverwrites.add(allowPlayerView(player)));
		String channelName = createMatchChannelName(match.getTeams());
		Category matchCategory = getOrCreateMatchCategory(server);
		return bot.getGuildById(match.getGame().getGuildId()).block()
				.createTextChannel(channelName)
				.withParentId(matchCategory.getId())
				.withPermissionOverwrites(permissionOverwrites);
	}

	private String createMatchChannelName(List<List<Player>> teams) {
		String tentativeString = String.join("-vs-", teams.stream()
				.map(team -> String.join("-", team.stream().map(Player::getTag).toList())).toList());
		if (tentativeString.length() <= 100) {
			return tentativeString;
		} else {
			return tentativeString.substring(0, 100);
		}
	}

	// Leaderboard
	public void refreshLeaderboard(Game game) {
		Message leaderboardMessage;
		try {
			leaderboardMessage = bot.getMessage(game.getLeaderboardMessageId(), game.getLeaderboardChannelId()).block();
		} catch (ClientException e) {
			leaderboardMessage = createLeaderboardChannelAndMessage(game);
			dbService.saveServer(game.getServer());// TODO muss das?
		}
		leaderboardMessage.edit()
				.withContent(Possible.of(Optional.empty()))
				.withEmbeds(EmbedBuilder.createRankingsEmbed(dbService.getLeaderboard(game))).subscribe();
	}

	private Message createLeaderboardChannelAndMessage(Game game) {
		Guild guild = bot.getGuildById(game.getGuildId()).block();
		TextChannel leaderboardChannel = guild.createTextChannel(String.format("%s Leaderboard", game.getName()))
				.withPermissionOverwrites(onlyBotCanSend(game.getServer()))
				.block();
		Message leaderboardMessage = leaderboardChannel.createMessage("creating leaderboard...").block();
		game.setLeaderboardMessageId(leaderboardMessage.getId().asLong());
		game.setLeaderboardChannelId(leaderboardChannel.getId().asLong());
		return leaderboardMessage;
	}

	// Categories
	public Category getOrCreateMatchCategory(Server server) {
		try {
			return (Category) bot.getChannelById(server.getMatchCategoryId()).block();
		} catch (ClientException e) {
			if (!e.getErrorResponse().get().getFields().get("message").toString().equals("Unknown Channel")
					&& !e.getErrorResponse().get().toString().contains("CHANNEL_PARENT_INVALID")) {
				throw e;
			}
			Guild guild = bot.getGuildById(server.getGuildId()).block();
			Category matchCategory = guild.createCategory("elo matches")
					.withPermissionOverwrites(excludePublic(server))
					.block();
			server.setMatchCategoryId(matchCategory.getId().asLong());
			dbService.saveServer(server);
			return matchCategory;
		}
	}

	public Category getOrCreateDisputeCategory(Server server) {// TODO evtl Optional<Guild> mit als parameter, um den request zu sparen?
		try {
			return (Category) bot.getChannelById(server.getDisputeCategoryId()).block();
		} catch (ClientException e) {
			if (!e.getErrorResponse().get().getFields().get("message").toString().equals("Unknown Channel")
					&& !e.getErrorResponse().get().toString().contains("CHANNEL_PARENT_INVALID")) {
				throw e;
			}
			Guild guild = bot.getGuildById(server.getGuildId()).block();
			Category disputeCategory = guild.createCategory("elo disputes")
					.withPermissionOverwrites(excludePublic(server))
					.block();
			server.setDisputeCategoryId(disputeCategory.getId().asLong());
			dbService.saveServer(server);
			return disputeCategory;
		}
	}

	public Category getOrCreateArchiveCategory(Server server) {
		List<Long> categoryIds = server.getArchiveCategoryIds();
		Category archiveCategory;
		int index = 0;
		while (true) {
			if (index >= categoryIds.size()) {
				Guild guild = bot.getGuildById(server.getGuildId()).block();
				archiveCategory = guild.createCategory(String.format("elo archive%s", index == 0 ? "" : index + 1))
						.withPermissionOverwrites(excludePublic(server))
						.block();
				categoryIds.add(archiveCategory.getId().asLong());
				dbService.saveServer(server);
				break;
			}
			try {
				archiveCategory = (Category) bot.getChannelById(categoryIds.get(index)).block();
			} catch (ClientException e) {
				if (!e.getErrorResponse().get().getFields().get("message").toString().equals("Unknown Channel")
						&& !e.getErrorResponse().get().toString().contains("CHANNEL_PARENT_INVALID")) {
					throw e;
				}
				Guild guild = bot.getGuildById(server.getGuildId()).block();
				archiveCategory = guild.createCategory(String.format("elo archive%s", index == 0 ? "" : " " + (index + 1)))
						.withPermissionOverwrites(excludePublic(server))
						.block();
				categoryIds.set(index, archiveCategory.getId().asLong());
				dbService.saveServer(server);
				break;
			}
			if (archiveCategory.getChannels().count().block() < 47) {
				break;
			}
			index++;
		}
		return archiveCategory;
	}

	public void moveToArchive(Server server, Channel channel) {
		Category archiveCategory = getOrCreateArchiveCategory(server);
		setParentCategory(channel, archiveCategory.getId().asLong()).subscribe();
		timedTaskQueue.addTimedTask(CHANNEL_DELETE, 60, channel.getId().asLong(), 0L, null);
		((TextChannel) channel).createMessage("**I have moved this channel to the archive. " +
				"I will delete this channel in one hour.**").subscribe();
	}

	public TextChannelEditMono setParentCategory(Channel channel, long categoryId) {
		return ((TextChannel) channel).edit().withParentId(Possible.of(Optional.of(Snowflake.of(categoryId))));
	}

	// view permissions
	public List<PermissionOverwrite> excludePublic(Server server) {// TODO die ganzen categories aufraeumen
		return new ArrayList<>(List.of(denyEveryoneView(server), allowAdminView(server), allowModView(server), allowBotView()));
	}

	private static PermissionOverwrite denyEveryoneView(Server server) {
		return PermissionOverwrite.forRole(Snowflake.of(server.getGuildId()),
				PermissionSet.none(),
				PermissionSet.of(Permission.VIEW_CHANNEL));
	}

	private static PermissionOverwrite allowAdminView(Server server) {
		return PermissionOverwrite.forRole(Snowflake.of(server.getAdminRoleId()),
				PermissionSet.of(Permission.VIEW_CHANNEL),
				PermissionSet.none());
	}

	private static PermissionOverwrite allowModView(Server server) {
		return PermissionOverwrite.forRole(Snowflake.of(server.getModRoleId()),
				PermissionSet.of(Permission.VIEW_CHANNEL),
				PermissionSet.none());
	}

	private static PermissionOverwrite allowPlayerView(Player player) {
		return PermissionOverwrite.forMember(Snowflake.of(player.getUserId()),
				PermissionSet.of(Permission.VIEW_CHANNEL),
				PermissionSet.none());
	}

	private PermissionOverwrite allowBotView() {
		return PermissionOverwrite.forMember(Snowflake.of(bot.botId),
				PermissionSet.of(Permission.VIEW_CHANNEL),
				PermissionSet.none());
	}
}