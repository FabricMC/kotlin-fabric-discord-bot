package discord

import (
	"fmt"
	"github.com/bwmarrin/discordgo"
)

type CommandContext struct {
	Content []string

	session *discordgo.Session
	message *discordgo.MessageCreate
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
	_, err := ctx.session.ChannelMessageSend(ctx.message.ChannelID, msg)
	return err
}

func (ctx *CommandContext) SendAuditMessage(action string) error {
	_, err := ctx.session.ChannelMessageSend(systemChannelID, fmt.Sprintf("%s peformed the follwing action: %s", ctx.message.Author.Username, action))
	return err
}

func (ctx *CommandContext) GetChannelName() (string, error) {
	channel, err := client.Channel(ctx.message.ChannelID)
	if err != nil {
		return "", err
	}

	channelName := channel.Name
	if channel.Type == discordgo.ChannelTypeDM || channel.Type == discordgo.ChannelTypeGroupDM {
		channelName = ctx.message.Author.Username
	}

	return channelName, nil
}

// Based off https://github.com/bwmarrin/discordgo/wiki/FAQ#determining-if-a-role-has-a-permission
func (ctx *CommandContext) HasPermission(permission int) (bool, error) {
	member, err := ctx.session.State.Member(ctx.message.GuildID, ctx.message.Author.ID)
	if err != nil {
		if member, err = ctx.session.GuildMember(ctx.message.GuildID, ctx.message.Author.ID); err != nil {
			return false, err
		}
	}

	// Iterate through the role IDs stored in member.Roles
	// to check permissions
	for _, roleID := range member.Roles {
		role, err := ctx.session.State.Role(ctx.message.GuildID, roleID)
		if err != nil {
			return false, err
		}
		if role.Permissions&permission != 0 {
			return true, nil
		}
	}

	return false, nil
}
