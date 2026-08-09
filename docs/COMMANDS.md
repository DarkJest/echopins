# Commands

Ordinary use needs no commands. These exist for players who prefer a list, and for admins.

All feedback is localized — command output follows the reader's language like the rest of the UI.

## Player commands

| Command | What it does |
|---|---|
| `/echopins` | Prints a short usage line |
| `/echopins list` | Pins near you that you are allowed to see |
| `/echopins mine` | Pins you created, newest first |
| `/echopins unread` | Pins you have not listened to yet |
| `/echopins stats` | Total pins, pins in this dimension, and how many are yours |
| `/echopins delete <id>` | Deletes one of your pins |

Listings show at most 20 entries and then say how many more there are. Each line is:

```
<author> · <length> · <dimension> (<x>, <y>, <z>) · <id>
```

Copy the id from there to use with `delete`.

`delete` is authorised server-side. Passing someone else's id returns "You don't have access to
this EchoPin." unless you are an operator.

## Admin commands

Require permission level `operatorPermissionLevel` (default `2`). The whole `admin` branch is
hidden from players below that level.

| Command | What it does |
|---|---|
| `/echopins admin stats` | Everything `stats` shows, plus active recordings, active playbacks, synced players, audio storage in use, and index size |
| `/echopins admin delete <id>` | Deletes any pin, skipping the ownership check |
| `/echopins admin purge expired` | Removes everything past its expiry now, in bounded batches |
| `/echopins admin purge player <player>` | Removes every pin by that player, and their audio |
| `/echopins admin cleanup` | Starts an orphan audio sweep |
| `/echopins admin reload` | Re-sends server settings to every connected player |

Admin deletions are broadcast to operators in the usual vanilla way, so moderation is visible.

### Notes

**`purge expired`** loops bounded batches rather than doing one unbounded sweep, so a large backlog
cannot stall the server thread inside a single command.

**`cleanup`** returns immediately and runs on the IO pool. Watch the log for the result:

```
[EchoPins/Server] Orphan sweep removed 3 unreferenced audio file(s)
```

It only deletes audio no pin references, including audio held by an unconfirmed recording, so it
is safe to run at any time.

**`reload`** does not re-read the config file — EchoPins reads config values live, so an edit is
already in effect. What `reload` does is push the derived values that clients cache (discovery
radius, caption limit, whether permanent pins are allowed) so their UI stops showing stale limits.

To re-read the file itself, use the vanilla `/reload` or restart.

## Examples

Find and remove your own pin:

```
/echopins mine
/echopins delete 0a3f1c62-4d0b-4a2e-9d17-2f5b7e1c8a44
```

Clear a griefer's messages:

```
/echopins admin purge player Griefer123
```

Reclaim disk after a large cleanup:

```
/echopins admin purge expired
/echopins admin cleanup
/echopins admin stats
```
