# Webhooks

Source: https://dev-docs.marketplace.cake.com/clockify/build/manifest/webhooks.html

---

## Definition

Webhooks are a way for your add-on to respond to events and triggers in real-time without the user directly interacting with the add-on UI itself. They can be used to integrate your add-on with Clockify in a seamless way.

Webhook messages are automatically sent by Clockify whenever an event that the add-on has subscribed to is triggered. Clockify provides a variety of Webhook Event types that an add-on can subscribe to according to its needs.

## Types

There are different types of webhooks that your add-on can subscribe to. The webhooks that are available for your add-on depend on the specific version of the manifest schema that you choose.

Generally, the following webhooks are available to add-ons:

- ASSIGNMENT_CREATED
- ASSIGNMENT_DELETED
- ASSIGNMENT_PUBLISHED
- ASSIGNMENT_UPDATED
- BILLABLE_RATE_UPDATED
- CLIENT_DELETED
- CLIENT_UPDATED
- COST_RATE_UPDATED
- EXPENSE_CREATED
- EXPENSE_DELETED
- EXPENSE_RESTORED
- EXPENSE_UPDATED
- LIMITED_USERS_ADDED_TO_WORKSPACE
- PROJECT_DELETED
- PROJECT_UPDATED
- TAG_DELETED
- TAG_UPDATED
- TASK_DELETED
- TASK_UPDATED
- TIME_ENTRY_RESTORED
- TIME_ENTRY_SPLIT
- TIME_OFF_REQUEST_UPDATED
- USER_EMAIL_CHANGED
- USER_GROUP_CREATED
- USER_GROUP_DELETED
- USER_GROUP_UPDATED
- USER_UPDATED
- USERS_INVITED_TO_WORKSPACE

You can test and visualize how the webhooks work and their respective payloads by triggering and listening for the events on your development environment.

## Requests

Webhook requests are POST requests that are sent to notify the add-on of events it has subscribed to. Each specific event will contain its specific payload as well as an accompanying signature that can be used to verify the request. After installing an add-on, you can view a list of all the registered webhooks by navigating to the add-ons tab and clicking on the webhooks option.

A list of all the registered webhooks along with their endpoints will be displayed.

You can access a webhook's logs by clicking on the webhook event. The logs will contain information such as the timestamp when the request was made, the HTTP status as well as the request and response bodies.

**Webhook logs are deleted after 7 days.**

## Signature

Each webhook that is dispatched by Clockify will contain a signature that can be used to verify its authenticity. A typical webhook request will contain the following request headers:

- **clockify-signature** — this represents the token that is signed on behalf of a single webhook type for a single add-on installation
- **clockify-webhook-event-type** — this represents the event that triggered the webhook, must be one of the webhook values above

### Webhook token

The webhook token that arrives as part of the clockify-signature headers does not expire. It contains the following claims that can be used to verify its authenticity and determine its context:

```json
{
  "iss": "clockify",
  "sub": "{add-on key}",
  "type": "addon",
  "workspaceId": "{workspace id}",
  "addonId": "{add-on id}"
}
```

- **iss** — the issuer of a JWT will always be clockify
- **sub** — the sub must be the same as the add-on key
- **type** — the type of a JWT will always be addon
- **workspaceId** — the ID where the add-on is installed and where the event was triggered
- **addonId** — the ID of the add-on installation on the workspace

## Authenticity

There are a couple of precautions that we must take to verify a webhook's authenticity and prevent request spoofing.

### Verify the JWT

The JWT token must be verified and the issuer and the sub claims must match the expected values for our add-on. To learn more about the tokens, visit the Authentication & Authorization section.

### Assert the webhook type is the one you expect

You must assert that the webhook types and the payloads supplied with the request match the webhook types that you expect for each endpoint.

### Compare webhook tokens

When an add-on which has defined an installed lifecycle gets installed on a workspace, an installation payload is provided along with the installed event. If the add-on has defined webhooks in its manifest, the payload will contain information regarding registered webhooks as well as the webhook token for each of them.

```json
{
  ...
  "webhooks": [
      {
         "authToken": "{token for the webhook}",
         "path": "{path defined in the manifest}",
         "webhookType": "ADDON"
      }
   ],
   ...
}
```

It is recommended that add-ons retrieve and store the authToken for each registered webhook, so that it can later be used to verify the authenticity of the requests.

The webhook token does not expire, and the same token for a particular webhook will be sent as part of the clockify-signature header for every webhook event of that type that is triggered on the workspace.
