# fabric-discord-bot

This bot is designed for a specific discord server, so it most likely will not fit the needs of your server.

## Configuration

Note that the bot can be configured using a config file called `config.toml` instead if you prefer. The part
of the environment variable name before the first underscore is the section you should use, and the rest
is the camelCase key you should use within that section.

For example, you might do this in a bash script:

```bash
export CHANNELS_ACTION_LOG=746875064200462416
```

In `config.toml`, you might do this:

```toml
[channels]
actionLog = 746875064200462416
```

You may also use system properties to configure the bot. Configuration takes the following order of precedence, with
later sources overriding earlier ones:

* `default.toml` bundled inside the JAR
* Environment variables
* System properties
* `config.toml` if it exists

### Required Environment Variables 

* BOT_TOKEN = The discord bot token
* GITHUB_OAUTH = GitHub OAuth token (if we end up using that functionality)

* DB_URL = MySQL URL, eg `mysql://host:port/database`
* DB_USERNAME = Database username to auth with
* DB_PASSWORD = Database password to auth with

### Optional Environment Variables 

* BOT_GUILD = The ID of the discord guild this bot should operate on
* BOT_PREFIX = The prefix required for commands

* CHANNELS_BOT_COMMANDS = The ID of the bot commands channel
* CHANNELS_ACTION_LOG = The ID of the action log channel
* CHANNELS_MODERATOR_LOG = The ID of the moderator log channel

* ROLES_ADMIN = The ID of the admin role
* ROLES_MOD = The ID of the moderator role
* ROLES_MUTED = The ID of the muted role

* ROLES_NO_META = The ID of the meta-muted role
* ROLES_NO_REACTIONS = The ID of the reactions-muted role
* ROLES_NO_REQUESTS = The ID of the requests=muted role
* ROLES_NO_SUPPORT = The ID of the support-muted role

## Stuff from the old bot

* GITHUB_TOKEN = The github personal access token, with admin:org permission
* GITHUB_ORG = The target github organisation for the github commands.
* DISCORD_MINECRAFT_CHANNELS = A comma separated list of discord channels to send minecraft version updates to
* DISCORD_JIRA_CHANNELS = A comma separated list of discord channels to send JIRA version updates to
