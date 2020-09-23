# fabric-discord-bot

This bot is designed for a specific discord server, so it most likely will not fit the needs of your server.

### Required Environment Variables 

* BOT_TOKEN = The discord bot token
* GITHUB_OAUTH = GitHub OAuth token (if we end up using that functionality)

### Optional Environment Variables 

* BOT_GUILD = The ID of the discord guild this bot should operate on
* BOT_PREFIX = The prefix required for commands

* CHANNELS_BOT_COMMANDS = The ID of the bot commands channel
* CHANNELS_ACTION_LOG = The ID of the action log channel
* CHANNELS_MODERATOR_LOG = The ID of the moderator log channel

* ROLES_ADMIN = The ID of the admin role
* ROLES_MOD = The ID of the moderator role
* ROLES_MUTED = The ID of the muted role

## Stuff from the old bot

* GITHUB_TOKEN = The github personal access token, with admin:org permission
* GITHUB_ORG = The target github organisation for the github commands.
* DISCORD_MINECRAFT_CHANNELS = A comma separated list of discord channels to send minecraft version updates to
* DISCORD_JIRA_CHANNELS = A comma separated list of discord channels to send JIRA version updates to
