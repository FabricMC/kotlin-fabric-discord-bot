package discord

import (
	"encoding/json"
	"errors"
	"github.com/FabricMC/fabric-discord-bot/backgroundcat"
	"github.com/FabricMC/fabric-discord-bot/utils"
	"github.com/bwmarrin/discordgo"
)

var (
	client          *discordgo.Session
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

	client.AddHandler(onMessage)
	client.AddHandler(backgroundcat.OnMessage)

	err = dg.Open()
	if err != nil {
		return err
	}

	return nil
}

func SendAnnouncement(channelID string, content string) error {
	message, err := client.ChannelMessageSend(channelID, content)
	if err != nil {
		return err
	}

	_, err = ChannelMessageCrosspost(client, message.ChannelID, message.ID)
	if err != nil {
		return err
	}

	return nil
}

func Close() {
	client.Close()
}

// Everything bellow this should be removed once https://github.com/bwmarrin/discordgo/pull/800 is pulled

var (
	endpointChannelMessageCrosspost = func(cID, mID string) string {
		return discordgo.EndpointChannel(cID) + "/messages/" + mID + "/crosspost"
	}
)

func ChannelMessageCrosspost(s *discordgo.Session, channelID string, messageID string) (st *discordgo.Message, err error) {
	endpoint := endpointChannelMessageCrosspost(channelID, messageID)

	body, err := s.RequestWithBucketID("POST", endpoint, nil, endpoint)
	if err != nil {
		return
	}

	err = unmarshal(body, &st)
	return
}

func unmarshal(data []byte, v interface{}) error {
	err := json.Unmarshal(data, v)
	if err != nil {
		return errors.New("json unmarshal")
	}

	return nil
}
