package com.elorankingbot.backend.command_legacy;

import com.elorankingbot.backend.commands.SlashCommand;
import com.elorankingbot.backend.service.Services;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.PermissionOverwrite;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.channel.Category;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;

public class Setup extends SlashCommand {

	private Guild guild;
	private Role adminRole;
	private Role modRole;
	private String reply;

	public Setup(ChatInputInteractionEvent event, Services services) {
		super(event, services);
	}

	public static ApplicationCommandRequest getRequest() {
		return ApplicationCommandRequest.builder()
				.name("setup")
				.description("Get started with the bot")
				.addOption(ApplicationCommandOptionData.builder()
						.name("nameofgame").description("The name of the game you want to track elo rating for")
						.type(3).required(true).build())
				.addOption(ApplicationCommandOptionData.builder()
						.name("allowdraw").description("Allow draw results and not just win or lose?")
						.type(5).required(true).build())
				.addOption(ApplicationCommandOptionData.builder()
						.name("adminrole").description("Choose an Elo Admin role. This role can change my settings. " +
								"Alternatively assign this later.")
						.type(8).required(false).build())
				.addOption(ApplicationCommandOptionData.builder()
						.name("moderatorrole").description("Choose an Elo Moderator role. This role is " +
								"for dispute resolution. Alternatively assign this later.")
						.type(8).required(false).build())
				.build();
	}

	public void execute() {
		/*
		reply = "Setup performed. Here is what I did:";
		guild = event.getInteraction().getGuild().block();
		game = new Game(event.getOption("nameofgame").get().getValue().get().asString(), guild.getId().asLong()
		);
		game.setAllowDraw(event.getOption("allowdraw").get().getValue().get().asBoolean());

		updateCommands().block();
		assignModAndAdminRole();
		setPermissionsForAdminCommands();
		setPermissionsForModCommands();
		TextChannel leaderboardChannel;
		try {
			TextChannel resultChannel = createResultChannel(guild, game);
			reply += String.format("\n- I created %s where I will post all match results.", resultChannel.getMention());
			createDisputeCategory();
			leaderboardChannel = createLeaderboardChannelAndMessage(guild, game);
			bot.updateLeaderboard(game);
		} catch (ClientException e) {
			if (e.getStatus().code() == 403) {
				event.reply("Insufficient permissions error while trying to create channels. " +
						"This can be caused by one of the following issues:\n" +
						"- My assigned permissions aren't sufficient. Go to `Server Settings -> Roles` and give me permission to manage channels.\n" +
						"- I do not meet the requirements of the verification level set for the server. " +
						"The verification level can be found at `Server Settings -> Moderation`. You can assign me any role " +
						"to get around the verification level requirements.").subscribe();
				return;
			} else {
				throw e;
			}
		}
		reply += String.format("\n- I created %s where I will display the leaderboard.", leaderboardChannel.getMention());

		service.saveGame(game);

		reply += String.format("\n- I created a web page with full rankings: http://%s/%s",
				services.applicationPropertiesLoader().getBaseUrl(), guildId);
		reply += String.format("\nFollow my announcement channel: <#%s>",
				services.applicationPropertiesLoader().getAnnouncementChannelId());
		event.reply(reply).doOnError(error -> System.out.println(error.getMessage())).subscribe();

		bot.sendToOwner(String.format("Setup performed on guild %s:%s with %s members",
				guild.getId().asLong(), guild.getName(), guild.getMemberCount()));
				*/


	}

	/*
	private void assignModAndAdminRole() {
		boolean adminRolePresent = event.getOption("adminrole").isPresent();
		boolean modRolePresent = event.getOption("moderatorrole").isPresent();
		Role everyoneRole = null;
		if (!adminRolePresent || !modRolePresent) everyoneRole = guild.getEveryoneRole().block();

		if (adminRolePresent) {
			adminRole = event.getOption("adminrole").get().getValue().get().asRole().block();
			reply += String.format("\n- I assigned Elo Admin permissions to %s", adminRole.getName());
		} else {
			adminRole = everyoneRole;
			reply += "\n- I assigned Elo Admin permissions to @everyone. I suggest you use /addpermission soon.";
		}
		game.setAdminRoleId(adminRole.getId().asLong());

		if (modRolePresent) {
			modRole = event.getOption("moderatorrole").get().getValue().get().asRole().block();
			reply += String.format("\n- I assigned Elo Moderator permissions to %s", modRole.getName());
		} else {
			modRole = everyoneRole;
			reply += "\n- I assigned Elo Moderator permissions to @everyone. I suggest you use /addpermission soon.";
		}
		game.setModRoleId(modRole.getId().asLong());
	}

	 */



	private void createDisputeCategory() {// TODO
		Category disputeCategory = guild.createCategory("elo disputes").withPermissionOverwrites(
				PermissionOverwrite.forRole(guild.getId(), PermissionSet.none(),
						PermissionSet.of(Permission.VIEW_CHANNEL)),
				PermissionOverwrite.forRole(adminRole.getId(), PermissionSet.of(Permission.VIEW_CHANNEL),
						PermissionSet.none()),
				PermissionOverwrite.forRole(modRole.getId(), PermissionSet.of(Permission.VIEW_CHANNEL),
						PermissionSet.none())).block();
		server.setDisputeCategoryId(disputeCategory.getId().asLong());
		reply += String.format("\n- I created a category %s where I will create dispute channels as needed. " +
				"It is only visible to Elo Admins and Moderators.", disputeCategory.getMention());
	}

	/*
	private Mono<Object> updateCommands() {
		Mono<Void> deleteSetup = bot.deleteCommand(guildId, Setup.getRequest().name());
		Mono<ApplicationCommandData> deployForcematch = bot.deployCommand(guildId, ForceMatch.getRequest(game.isAllowDraw()));
		Mono<ApplicationCommandData> deployChallenge = bot.deployCommand(guildId, Challenge.getRequest());
		Mono<ApplicationCommandData> deployUserInteractionChallenge = bot.deployCommand(guildId, ChallengeAsUserInteraction.getRequest());
		Mono<ApplicationCommandData> deployReset = bot.deployCommand(guildId, Reset.getRequest());
		Mono<ApplicationCommandData> deployPermission = bot.deployCommand(guildId, com.elorankingbot.backend.commands.admin.SetRole.getRequest());
		Mono<ApplicationCommandData> deploySet = bot.deployCommand(guildId, Set.getRequest());
		Mono<ApplicationCommandData> deployRating = bot.deployCommand(guildId, Rating.getRequest());
		Mono<ApplicationCommandData> deployBan = bot.deployCommand(guildId, Ban.getRequest());
		Mono<ApplicationCommandData> deployInfo = bot.deployCommand(guildId, Info.getRequest());
		reply += "\n- I updated my commands on this server. This may take a minute to update.";
		return Mono.zip(List.of(
						deleteSetup, deployForcematch, deployChallenge, deployUserInteractionChallenge,
						deployReset, deployPermission, deploySet, deployRating, deployBan, deployInfo),
				object -> null);
	}

	private void setPermissionsForAdminCommands() {
		service.getAdminCommands().forEach(commandName ->
				bot.setDiscordCommandPermissions(guildId, commandName, adminRole));
	}

	private void setPermissionsForModCommands() {
		service.getModCommands().forEach(commandName ->
				bot.setDiscordCommandPermissions(guildId, commandName, adminRole, modRole));
	}

	 */
}