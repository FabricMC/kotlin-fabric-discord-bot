package commands

import (
	"fmt"
	"strconv"
	"strings"

	"github.com/FabricMC/fabric-discord-bot/discord"
	"github.com/FabricMC/fabric-discord-bot/github"
)

func GithubCommand(ctx *discord.CommandContext) error {
	if len(ctx.Content) < 2 {
		err := ctx.SendMessage("Invalid arguments")
		return err
	}

	//TODO make a whole message system for this stuff, for now this will do
	switch ctx.Content[0] {
	case "ban", "block":
		return banCommand(ctx)
	case "unban", "unblock":
		return unbanCommand(ctx)
	case "lockall":
		return lockAllCommand(ctx)
	default:
		err := ctx.SendMessage("Sub command not found")
		return err
	}
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

func lockAllCommand(ctx *discord.CommandContext) error {
	if len(ctx.Content) < 3 {
		err := ctx.SendMessage("Invalid arguments. Usage: `github lockall <repository> <user> [reason]`. If no reason is given, the default is `spam`")
		return err
	}
	repository := ctx.Content[1]
	user := ctx.Content[2]
	var reason string
	switch len(ctx.Content) {
	case 3:
		{
			reason = "spam"
		}
	case 4:
		{
			reason = ctx.Content[3]
		}
	default:
		{
			// blegh
			if ctx.Content[3] == "too" && ctx.Content[4] == "heated" {
				reason = "too heated"
			}
		}
	}

	if !isValidLockReason(reason) {
		err := ctx.SendMessage("Invalid lock reason. Valid reasons: `spam`, `off-topic`, `too heated`, `resolved`")
		return err
	}

	lockedIssues, err := github.LockAll(github.Organization, repository, user, reason)

	var builder strings.Builder
	builder.WriteRune('#')
	builder.WriteString(strconv.FormatInt(lockedIssues[0], 10))

	for _, i := range lockedIssues[1:] {
		builder.WriteString(", ")
		builder.WriteRune('#')
		builder.WriteString(strconv.FormatInt(i, 10))
	}
	readable := builder.String()

	var msg string
	switch len(lockedIssues) {
	case 0:
		msg = "Locked nothing."
	case 1:
		msg = fmt.Sprintf("Closed and locked %s in repository **https://github.com/%s/%s**.", readable, github.Organization, repository)
	default:
		msg = fmt.Sprintf("Closed and locked **%d** issues/PRs in repository **https://github.com/%s/%s**: %s.", len(lockedIssues), github.Organization, repository, readable)
	}

	if err == nil {
		ctx.SendMessageWithAudit(
			msg,
			fmt.Sprintf("closed and locked all issues/PRs from **%s** in the **%s/%s** repository with reason **%s**.\n\nLocked %s.", user, github.Organization, repository, reason, readable),
		)
	} else {
		ctx.SendMessageWithAudit(
			msg,
			fmt.Sprintf("closed and locked all issues/PRs from **%s** in the **%s/%s** repository with reason **%s** (errors occured).\n\nLocked %s.", user, github.Organization, repository, reason, readable),
		)
	}

	return err
}

func isValidLockReason(reason string) bool {
	switch reason {
	case "off-topic",
		"too heated",
		"resolved",
		"spam":
		return true
	default:
		return false
	}
}
