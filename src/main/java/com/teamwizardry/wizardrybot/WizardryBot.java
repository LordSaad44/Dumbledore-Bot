package com.teamwizardry.wizardrybot;


import ai.api.model.Result;
import com.teamwizardry.wizardrybot.api.*;
import com.teamwizardry.wizardrybot.api.math.RandUtil;
import com.teamwizardry.wizardrybot.api.twilio.TwilioWebListener;
import com.teamwizardry.wizardrybot.module.ModuleAboutCommand;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import org.apache.commons.lang3.StringUtils;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.message.Message;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reflections.Reflections;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

public class WizardryBot {

	public static WizardryBot wizardryBot;
	public static DiscordApi API;

	public static ArrayList<Module> modules = new ArrayList<>();
	private static HashSet<String> commands = new HashSet<>();

	public static float THINKTHRESHHOLD = 0.85f;

	@Nullable
	public static File ffmpegExe = null;
	@Nullable
	public static File ffProbe = null;

	private static boolean DEV = false;

	public static void main(String[] args) {
		//	CluedoCards.init();

		//	if (false) {
		wizardryBot = new WizardryBot();
		if (args.length <= 0 || args[0].isEmpty()) {
			System.out.println("No key provided.");
			return;
		}

		if (args.length > 1) {
			if (args[1].equals("dev")) DEV = true;
		}

		String KEY = args[0];

		System.out.println("Checking bot authorization...");
		if (!Keys.authorize(KEY)) return;
		System.out.println("Authorized!");

		new DiscordApiBuilder().setToken(KEY).login().thenAccept(api -> {
			System.out.println(api.createBotInvite());
			init(api);
			System.out.println("YOU SHALL NOT PASS!");
		}).exceptionally(throwable -> {
			throwable.printStackTrace();
			return null;
		});
		//	}
	}

	private static void init(DiscordApi api) {

		api.getChannelById(479341551668166657L).ifPresent(serverChannel -> {
			//	new TwilioWebListener(api, serverChannel);
		});

		BufferedImage profile = Utils.downloadURLAsImage(null, Constants.albusProfileLinks[RandUtil.nextInt(Constants.albusProfileLinks.length - 1)]);
		if (profile != null) {
			api.updateAvatar(profile);
		}

		// INSTALLERS
		{
			System.out.println("<<------------------------------------------------------------------------>>");
			File binDir = new File("bin/");
			if (!binDir.exists()) binDir.mkdirs();

			{
				try {
					File domainsFile = new File(binDir, "domains.txt");

					if (!domainsFile.exists()) {
						System.out.println("domains whitelist does not exist! Downloading...");

						URL urlObject = new URL("https://paste.ee/r/XGYeP");
						URLConnection urlConnection = urlObject.openConnection();
						urlConnection.setRequestProperty("User-Agent", "Google Chrome Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36.");

						try (InputStream in = urlConnection.getInputStream()) {
							Files.copy(in, domainsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

							System.out.println("Successfully downloaded domains whitelist!");
						}

					}

					if (domainsFile.exists()) {
						Domains.INSTANCE.init(domainsFile);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			{
				File ffmpegDir = new File(binDir, "ffmpeg/");
				if (!ffmpegDir.exists()) ffmpegDir.mkdirs();

				File ffmpegZip = new File(ffmpegDir, "ffmpegZip.zip");
				File ffmpegExtractDir = new File(ffmpegDir, "ffmpeg-4.0.2-win64-static");

				if (!ffmpegZip.exists()) {
					try {
						System.out.println("ffmpeg does not exist! Downloading...");
						URL urlObject = new URL("https://ffmpeg.zeranoe.com/builds/win64/static/ffmpeg-4.0.2-win64-static.zip");
						URLConnection urlConnection = urlObject.openConnection();
						urlConnection.setRequestProperty("User-Agent", "Google Chrome Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36.");

						try (InputStream in = urlConnection.getInputStream()) {
							Files.copy(in, ffmpegZip.toPath(), StandardCopyOption.REPLACE_EXISTING);

							ZipFile zipFile = new ZipFile(ffmpegZip.getPath());
							zipFile.extractAll(ffmpegDir.getAbsolutePath());
							System.out.println("Successfully downloaded ffmpeg!");
						} catch (ZipException e) {
							e.printStackTrace();
						}

					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				if (!ffmpegExtractDir.exists()) {
					System.out.println("Could not download ffmpeg!!");
				}

				File exe = new File(ffmpegExtractDir, "bin/ffmpeg.exe");
				File probe = new File(ffmpegExtractDir, "bin/ffprobe.exe");

				if (exe.exists()) ffmpegExe = exe;
				if (probe.exists()) ffProbe = probe;
			}

			{
				File youtubeDL = new File(binDir, "youtube-dl.exe");

				if (!youtubeDL.exists()) {

					try {
						System.out.println("Youtube-dl does not exist! Downloading...");
						URL urlObject = new URL("https://yt-dl.org/latest/youtube-dl.exe");
						URLConnection urlConnection = urlObject.openConnection();
						urlConnection.setRequestProperty("User-Agent", "Google Chrome Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36.");

						try (InputStream in = urlConnection.getInputStream()) {
							Files.copy(in, youtubeDL.toPath(), StandardCopyOption.REPLACE_EXISTING);

							System.out.println("Successfully downloaded youtube-dl.exe!");
						}

					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}

			System.out.println("Installation Complete");
			System.out.println("<<------------------------------------------------------------------------>>");
		}

		// --- Modules and Commands init --- //
		{
			Reflections reflections = new Reflections("com.teamwizardry.wizardrybot.module");
			Set<Class<? extends Module>> classes = reflections.getSubTypesOf(Module.class);
			for (Class<? extends Module> clazz : classes) {
				try {
					modules.add(clazz.newInstance());
				} catch (InstantiationException | IllegalAccessException e) {
					e.printStackTrace();
				}
			}
			for (Module module : modules) {
				module.init(api);
				commands.add(module.getName().toLowerCase());
				if (module instanceof ICommandModule) {
					ICommandModule cmd = (ICommandModule) module;
					Collections.addAll(commands, cmd.getAliases());
				}
			}
		}

		api.addMessageCreateListener(messageCreateEvent -> {
			if (messageCreateEvent.getChannel().getId() == 479341551668166657L) {

				Message message = messageCreateEvent.getMessage();
				if (messageCreateEvent.getMessage().getContent().startsWith("<WHATSAPP=")) {
					processMessage(message, messageCreateEvent.getApi(), true);
				} else {
					for (Message history : messageCreateEvent.getChannel().getMessagesAsStream().limit(20).collect(Collectors.toList())) {
						if (history.getContent().startsWith("<WHATSAPP=")) {
							String number = StringUtils.substringBetween(history.getContent(), "<WHATSAPP=", ">");

							TwilioWebListener.sendMessage(number, message);
							return;
						}
					}
				}
			} else messageCreateEvent.getMessage().getUserAuthor().ifPresent(user -> {

					if (user.isBot() || user.isYourself()) return;
					processMessage(messageCreateEvent.getMessage(), messageCreateEvent.getApi(), false);
				});
		});
	}

	public static void processMessage(Message message, DiscordApi api, boolean whatsapp) {
		Command command = new Command(message, commands);

		Result result = command.getResult();
		Statistics.INSTANCE.addToStat("messages_analyzed");

		boolean shouldRespond = shouldRespond(command);

		HashSet<Module> priorityList = new HashSet<>();
		for (Module module : modules) {
			if (module.isDisabled()) continue;

			if (shouldRespond || module.overrideResponseCheck()) {
				module.onMessage(api, message, result, command, whatsapp);

				if (module instanceof ICommandModule) {

					ICommandModule moduleCmd = (ICommandModule) module;

					if (command.getCommand() != null
							&& Arrays.asList(moduleCmd.getAliases()).contains(command.getCommand())) {

						priorityList.add(module);

					} else if (moduleCmd.getActionID() != null) {
						if (doesPassResult(result, moduleCmd.getActionID())) {
							priorityList.add(module);
						}
					}
				}
			}
		}

		Module highestPriority = null;
		for (Module module : priorityList) {
			if (highestPriority == null) highestPriority = module;
			else if (module.getPriority() > highestPriority.getPriority()) highestPriority = module;
		}
		if (highestPriority != null) {
			if (highestPriority instanceof ICommandModule) {

				final ICommandModule cmdModule = ((ICommandModule) highestPriority);
				Module finalHighestPriority = highestPriority;

				Thread thread = new Thread(() -> {

					if (!cmdModule.onCommand(api, message, command, result, whatsapp)) {
						message.getChannel().sendMessage("That's not how you use that command.");
						ModuleAboutCommand.sendCommandMessage(message, finalHighestPriority);
					}
					Statistics.INSTANCE.addToStat("commands_triggered");
				});
				thread.start();
			}
		} else {

			if (command.getCommand() == null || command.getCommand().isEmpty()) return;

			for (String cmd : commands) {
				if (command.getCommand().toLowerCase().equals(cmd.toLowerCase())) {

					Module reverse = null;
					for (Module module : modules) {
						if (module.getName().toLowerCase().contains(cmd) || module.getName().toLowerCase().replace(" ", "").contains(cmd)
								|| cmd.contains(module.getName().toLowerCase()) || cmd.contains(module.getName().toLowerCase().replace(" ", ""))) {
							reverse = module;
							break;
						}
						if (module instanceof ICommandModule) {
							ICommandModule reverseCMD = (ICommandModule) module;
							for (String alias : reverseCMD.getAliases()) {
								if (alias.contains(cmd)) {
									reverse = module;
									break;
								}
							}
						}
					}

					if (reverse != null) {
						if (!reverse.overrideIncorrectUsage()) {
							message.getChannel().sendMessage("That's not how you use that command.");
							ModuleAboutCommand.sendCommandMessage(message, reverse);
						}
					}
				}
			}
		}
	}

	private static boolean shouldRespond(Command command) {
		return command.hasSaidHey();
	}

	public static boolean doesPassResult(@Nullable Result result, @NotNull String actionID) {
		return result != null
				&& result.getAction() != null
				&& result.getAction().equals(actionID)
				&& result.getScore() >= THINKTHRESHHOLD;
	}
}
