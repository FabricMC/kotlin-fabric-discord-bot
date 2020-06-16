package commands

import (
	"fmt"
	"github.com/FabricMC/fabric-discord-bot/discord"
	"github.com/FabricMC/fabric-discord-bot/github"
)

func GithubCommand(ctx *discord.CommandContext) error {
	if len(ctx.Content) != 2 {
		err := ctx.SendMessage("Invalid arguments")
		if err != nil {
			return err
		}
		return nil
	}

	//TODO make a whole message system for this stuff, for now this will do
	subCommand := ctx.Content[0]
	if subCommand == "ban" || subCommand == "block" {
		return banCommand(ctx)
	} else if subCommand == "unban" || subCommand == "unblock" {
		return unbanCommand(ctx)
	} else {
		err := ctx.SendMessage("Sub command not found")
		if err != nil {
			return err
		}
	}
	return nil
}

func banCommand(ctx *discord.CommandContext) error {
	user := ctx.Content[1]

	blocked, err := github.BlockUser(github.Organization, user)
	if err != nil {
		return err
	}

	if !blocked {
		err := ctx.SendMessageWithAudit("This user has been blocked from the organisation", fmt.Sprintf("blocked **%s** from the **%s** github organisation", user, github.Organization))
		if err != nil {
			return err
		}
	} else {
		err := ctx.SendMessage("This user has already been blocked from the organisation")
		if err != nil {
			return err
		}
	}

	return nil
}

func unbanCommand(ctx *discord.CommandContext) error {
	user := ctx.Content[1]
	unBlocked, err := github.UnblockUser(github.Organization, user)
	if err != nil {
		return err
	}

	if unBlocked {
		err := ctx.SendMessageWithAudit("The user has been unblocked from the organisation", fmt.Sprintf("unblocked **%s** from the **%s** github organisation", user, github.Organization))
		if err != nil {
			return err
		}
	} else {
		err := ctx.SendMessage("The user is not blocked from the organisation")
		if err != nil {
			return err
		}
	}

	return nil
}
