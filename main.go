package main

import (
	"github.com/FabricMC/fabric-discord-bot/commands"
	"github.com/FabricMC/fabric-discord-bot/discord"
	"github.com/FabricMC/fabric-discord-bot/github"
	"log"
	"os"
	"os/signal"
	"syscall"
)

func main() {
	log.Println("Starting fabric-discord-bot")

	err := discord.RegisterCommandHandler("!github", commands.GithubCommand)
	if err != nil {
		log.Fatal("Failed to register command", err)
	}

	err = discord.Connect()
	if err != nil {
		log.Fatal("Failed to connect to discord", err)
	}

	err = github.Connect()
	if err != nil {
		log.Fatal("Failed to connect to github", err)
	}

	// Wait here until CTRL-C or other term signal is received.
	log.Println("Bot is now running.  Press CTRL-C to exit.")
	sc := make(chan os.Signal, 1)
	signal.Notify(sc, syscall.SIGINT, syscall.SIGTERM, os.Interrupt, os.Kill)
	<-sc

	discord.Close()
}
