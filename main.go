package main

import (
	"github.com/FabricMC/fabric-discord-bot/commands"
	"github.com/FabricMC/fabric-discord-bot/discord"
	"github.com/FabricMC/fabric-discord-bot/github"
	"github.com/FabricMC/fabric-discord-bot/utils"
	"github.com/FabricMC/fabric-discord-bot/versioncheck"
	"github.com/bwmarrin/discordgo"
	"log"
	"os"
	"os/signal"
	"syscall"
)

func main() {
	log.Println("Starting fabric-discord-bot")

	if utils.HasEnv("GITHUB_TOKEN") {
		err := discord.RegisterCommandHandler("!github", discordgo.PermissionBanMembers, commands.GithubCommand)
		if err != nil {
			log.Fatal("Failed to register !github command", err)
		}

		err = github.Connect()
		if err != nil {
			log.Fatal("Failed to connect to github", err)
		}
	}

	err := discord.RegisterCommandHandler("!slowmode", discordgo.PermissionManageMessages, commands.SlowmodeCommand)
	if err != nil {
		log.Fatal("Failed to register !slowmode command", err)
	}

	err = discord.Connect()
	if err != nil {
		log.Fatal("Failed to connect to discord", err)
	}

	if utils.HasEnv("DISCORD_MINECRAFT_CHANNELS") {
		err = versioncheck.Setup()
		if err != nil {
			log.Fatal("Failed to setup version check", err)
		}
	}

	// Wait here until CTRL-C or other term signal is received.
	log.Println("Bot is now running.  Press CTRL-C to exit.")
	sc := make(chan os.Signal, 1)
	signal.Notify(sc, syscall.SIGINT, syscall.SIGTERM, os.Interrupt)
	<-sc

	discord.Close()
}
