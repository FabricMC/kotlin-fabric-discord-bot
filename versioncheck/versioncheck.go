package versioncheck

import (
	"fmt"
	"github.com/FabricMC/fabric-discord-bot/discord"
	"github.com/FabricMC/fabric-discord-bot/utils"
	"strings"
	"time"
)

func Setup() error {
	_, err := utils.GetEnv("DISCORD_MINECRAFT_CHANNELS")
	if err != nil {
		return err
	}

	_, err = utils.GetEnv("DISCORD_JIRA_CHANNELS")
	if err != nil {
		return err
	}

	err = populateInitialJiraVersions()
	if err != nil {
		return err
	}

	err = populateInitialMinecraftVersions()
	if err != nil {
		return err
	}

	ticker := time.NewTicker(time.Second * 30)
	go func() {
		for range ticker.C {
			doUpdateCheck()
		}
	}()

	return nil
}

func doUpdateCheck() {
	go jiraUpdateCheck(postJiraMessage)
	go minecraftUpdateCheck(postGameMessage)
}

func postGameMessage(message string) error {
	fmt.Println(message)

	channels, err := getChannelsFromEnv("DISCORD_MINECRAFT_CHANNELS")
	if err != nil {
		return err
	}

	for _, channel := range channels {
		discord.SendAnnouncement(channel, message)
	}

	return nil
}

func postJiraMessage(message string) error {
	fmt.Println(message)

	channels, err := getChannelsFromEnv("DISCORD_JIRA_CHANNELS")
	if err != nil {
		return err
	}

	for _, channel := range channels {
		discord.SendAnnouncement(channel, message)
	}

	return nil
}

func getChannelsFromEnv(key string) ([]string, error) {
	value, err := utils.GetEnv(key)
	if err != nil {
		return nil, err
	}

	return strings.Split(value, ","), nil
}
