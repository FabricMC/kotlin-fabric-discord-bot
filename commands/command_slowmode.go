package commands

import (
	"encoding/json"
	"fmt"
	"log"
	"regexp"
	"strconv"

	"github.com/FabricMC/fabric-discord-bot/discord"
	"github.com/bwmarrin/discordgo"
)

var channelMentionRegex = regexp.MustCompile(`<#(?P<id>\d+)>`)

const maxSlowmodeInterval = 21600 // https://discord.com/developers/docs/resources/channel#modify-channel

func SlowmodeCommand(ctx *discord.CommandContext) error {
	if len(ctx.Content) < 1 {
		return ctx.SendMessage("Invalid Arguments. Usage: !slowmode <time in seconds|off> [#channel]")
	}
	timearg := ctx.Content[0]
	var time uint64
	if timearg == "off" {
		time = 0
	} else {
		var err error
		time, err = strconv.ParseUint(timearg, 10, 16)
		if err != nil || time > maxSlowmodeInterval {
			return ctx.SendMessage(fmt.Sprintf("Invalid slowmode interval, must be between 0 and %d", maxSlowmodeInterval))
		}
	}

	var channelID string
	if len(ctx.Content) >= 2 {
		matches := channelMentionRegex.FindStringSubmatch(ctx.Content[1])
		if len(matches) != 2 {
			return ctx.SendMessage("Invalid channel.")
		}
		channelID = matches[1]
	} else {
		channelID = ctx.Message.ChannelID
	}

	log.Println(channelID)
	var err error
	if time > 0 {
		_, err = ctx.Session.ChannelEditComplex(channelID, &discordgo.ChannelEdit{RateLimitPerUser: int(time)})
	} else {
		/*
			(Apple:)
			Blegh. Discordgo's structs, when marshalled with json.Marshal(), don't
			include empty values, due to the `json:"omitempty"` directive on all their fields.
			This makes sense, because this is a PATCH endpoint, but the issue is that
			RateLimitPerUser is an int.
			Ints can't be nil, but they still have a defined empty value: 0.
			This is very inconvenient for us because the json marshaller sees the 0 and
			the `omitempty` directive, assumes the 0 is a default value, and doesn't include it.
			Discord then interprets not sending this field as not wanting to change it.

			To work around this, we send the request to the channel endpoint ourselves
			with a custom struct that doesn't omit 0 values and all is well in the world.

			Except that the upstream fix for this - changing the int field to *int which can
			discern between nil and 0 - is a breaking change.
			Also, this problem spans across the entirety of discordgo.

			Yay.
		*/
		var body []byte
		body, err = ctx.Session.RequestWithBucketID("PATCH",
			discordgo.EndpointChannel(channelID),
			struct {
				RateLimit uint64 `json:"rate_limit_per_user"`
			}{time},
			discordgo.EndpointChannel(channelID),
		)

		if err != nil {
			return err
		}
		err = json.Unmarshal(body, &discordgo.Channel{})
	}

	return err
}
