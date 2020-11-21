# TCP chat

A TCP chat application written in Java for the Computer Networking class assignment.

Things done:

16/11/20
* Parsed buffer to distinguish between commands: start at "/nick"!
* Sent back response ("OK") to client who typed command.

21/11/20
* Server can now distinguish between multiple commands sent at once (using a command queue).
* Chat rooms were added
* Users can now enter chat rooms and talk with each other!
* The only problem now is that there isn't a way yet to leave the chat room (and the rooms are not deleted). That's where we should work on next.
* We still need to work on the delimiters in the application message. Since TCP doesn't know where a message begins or ends, we have to create a specification that tells the application that. I'm thinking of using, just like SMTP, a line with only a '.' to mark the end of a message. In the messages, it could go something like this: MESSAGE nickname \n {Message with " ", "/", "\n", etc}\n.\n. The "\n.\n" part marks the end of the message.


