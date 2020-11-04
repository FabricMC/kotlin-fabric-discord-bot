# Tags

This subproject contains the tags parser, and a way to validate all the tags in a given directory. The built
JAR is runnable, and will attempt to find and load any `.tag` files it can find by walking the current working
directory (or a directory specified on the command line).

If you're using the Docker container (`fabric-discord-bot-tags`), remember to mount `/tags` in the container to the
directory containing your tags.
