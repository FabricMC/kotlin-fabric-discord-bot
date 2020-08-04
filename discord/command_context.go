package discord

import (
	"fmt"
	"github.com/bwmarrin/discordgo"
)

type CommandContext struct {
	Content []string

	Session *discordgo.Session
	Message *discordgo.MessageCreate
}

func (ctx *CommandContext) SendMessageWithAudit(msg string, action string) error {
	err := ctx.SendMessage(msg)
	if err != nil {
		return err
	}

	err = ctx.SendAuditMessage(action)
	if err != nil {
		return err
	}

	return nil
}

func (ctx *CommandContext) SendMessage(msg string) error {
	_, err := ctx.Session.ChannelMessageSend(ctx.Message.ChannelID, msg)
	return err
}

func (ctx *CommandContext) SendAuditMessage(action string) error {
	_, err := ctx.Session.ChannelMessageSend(systemChannelID, fmt.Sprintf("%s performed the following action: %s", ctx.Message.Author.Username, action))
	return err
}

func (ctx *CommandContext) GetChannelName() (string, error) {
	channel, err := client.Channel(ctx.Message.ChannelID)
	if err != nil {
		return "", err
	}

	channelName := channel.Name
	if channel.Type == discordgo.ChannelTypeDM || channel.Type == discordgo.ChannelTypeGroupDM {
		channelName = ctx.Message.Author.Username
	}

	return channelName, nil
}

// Based off https://github.com/bwmarrin/discordgo/wiki/FAQ#determining-if-a-role-has-a-permission
func (ctx *CommandContext) HasPermission(permission int) (bool, error) {
	member, err := ctx.Session.State.Member(ctx.Message.GuildID, ctx.Message.Author.ID)
	if err != nil {
		if member, err = ctx.Session.GuildMember(ctx.Message.GuildID, ctx.Message.Author.ID); err != nil {
			return false, err
		}
	}

	// Iterate through the role IDs stored in member.Roles
	// to check permissions
	for _, roleID := range member.Roles {
		role, err := ctx.Session.State.Role(ctx.Message.GuildID, roleID)
		if err != nil {
			return false, err
		}
		if role.Permissions&permission != 0 {
			return true, nil
		}
	}

	return false, nil
}
