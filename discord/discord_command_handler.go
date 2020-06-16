package discord

import (
	"errors"
	"github.com/bwmarrin/discordgo"
	"log"
	"strings"
)

var (
	commandMap = map[string]*Command{}
)

func onMessage(s *discordgo.Session, m *discordgo.MessageCreate) {
	// Dont handle our own messages
	if m.Author.ID == s.State.User.ID {
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
			ctx := CommandContext{messageSplit[1:], s, m}
			permission, err := ctx.HasPermission(command.requiredPermission)

			if err == nil && permission {
				err = command.commandHandler(&ctx)
				if err != nil {
					log.Println("an error occurred when processing command "+messageSplit[0], err)

					err = ctx.SendMessage("An error occurred when processing command, see bot log for more details")
					if err != nil {
						// What can we do now ;)
					}
				}
			} else {
				// No permission, going to do nothing for now
			}
		}
	}
}

type Command struct {
	commandHandler     func(ctx *CommandContext) error
	requiredPermission int
}

func RegisterCommandHandler(command string, permissions int, commandHandler func(ctx *CommandContext) error) error {
	_, exists := commandMap[command]

	if exists {
		return errors.New("duplicate command")
	}

	commandMap[command] = &Command{commandHandler, permissions}

	return nil
}
