package discord

import (
	"errors"
	"github.com/bwmarrin/discordgo"
	"log"
	"strings"
)

var (
	commandMap = map[string]func(ctx *CommandContext) error{}
)

func onMessage(s *discordgo.Session, m *discordgo.MessageCreate) {
	// Dont handle our own messages
	if m.Author.ID == botId {
		return
	}

	messageContent := m.Content
	messageSplit := strings.Split(messageContent, " ")

	if len(messageSplit) == 0 {
		return
	}

	// Check if the command stats with !
	if strings.HasPrefix(messageSplit[0], "!") {
		command, exists := commandMap[messageSplit[0]]
		if exists {
			handler := CommandContext{messageSplit[1:], s, m}
			err := command(&handler)
			if err != nil {
				log.Println("an error occurred when processing command", err)

				err = handler.SendMessage("An error occurred when processing command, see bot log for more details")
				if err != nil {
					// What can we do now ;)
				}
			}
		}
	}
}

func RegisterCommandHandler(command string, commandHandler func(ctx *CommandContext) error) error {
	_, exists := commandMap[command]

	if exists {
		return errors.New("duplicate command")
	}

	commandMap[command] = commandHandler

	return nil
}
