package discord

import (
	"github.com/FabricMC/fabric-discord-bot/utils"
	"github.com/bwmarrin/discordgo"
)

var (
	client          *discordgo.Session
	botId           string
	systemChannelID string
)

func Connect() error {
	token, err := utils.GetEnv("DISCORD_TOKEN")
	if err != nil {
		return err
	}

	scID, err := utils.GetEnv("DISCORD_SYSTEM_CHANNEL")
	if err != nil {
		return err
	}
	systemChannelID = scID

	dg, err := discordgo.New("Bot " + token)
	if err != nil {
		return err
	}
	client = dg

	u, err := dg.User("@me")
	if err != nil {
		return err
	}
	botId = u.ID

	client.AddHandler(onMessage)

	err = dg.Open()
	if err != nil {
		return err
	}

	return nil
}

func Close() {
	client.Close()
}
