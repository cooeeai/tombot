# tombot

A Bot for Facebook Messenger using Scala and Akka-HTTP.

Includes Scala API for Facebook Messenger.

Simple test at this stage. Will echo any message, except when `/buy` slash command is used, which will return a
"Call To Action" (CTA) bubble showing product details and option to buy.

[![Deploy to Heroku](https://www.herokucdn.com/deploy/button.png)](https://heroku.com/deploy)

<table width="100%" style="border: none; margin-top: 20px;">
    <tr>
        <td style="border: none;">
            <img src="assets/echo.png" title="Echo action" width="231" height="300">
            <br>
            Echo action
        </td>
        <td style="border: none;">
            <img src="assets/buy.png" title="Echo action" width="231" height="300">
            <br>
            Buy action
        </td>
    </tr>
</table>

<img src="assets/thread.png" title="Echo action" width="368" height="500">

Message thread