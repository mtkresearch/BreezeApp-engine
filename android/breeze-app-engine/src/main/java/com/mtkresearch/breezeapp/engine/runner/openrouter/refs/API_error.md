---
title: Errors
subtitle: API Errors
headline: API Error Handling | OpenRouter Error Documentation
canonical-url: 'https://openrouter.ai/docs/api-reference/errors'
'og:site_name': OpenRouter Documentation
'og:title': API Error Handling - Complete Guide to OpenRouter Errors
'og:description': >-
  Learn how to handle errors in OpenRouter API interactions. Comprehensive guide
  to error codes, messages, and best practices for error handling.
'og:image':
  type: url
  value: >-
    https://openrouter.ai/dynamic-og?title=API%20Error%20Handling%20-Errors&description=Learn%20how%20to%20handle%20errors%20in%20OpenRouter%20API%20interactions.%20Comprehensive%20guide%20to%20error%20codes,%20messages,%20and%20best%20practices%20for%20error%20handling.
'og:image:width': 1200
'og:image:height': 630
'twitter:card': summary_large_image
'twitter:site': '@OpenRouterAI'
noindex: false
nofollow: false
---

import { HTTPStatus } from '../../../imports/constants';

For errors, OpenRouter returns a JSON response with the following shape:

```typescript
type ErrorResponse = {
  error: {
    code: number;
    message: string;
    metadata?: Record<string, unknown>;
  };
};
```

The HTTP Response will have the same status code as `error.code`, forming a request error if:

- Your original request is invalid
- Your API key/account is out of credits

Otherwise, the returned HTTP response status will be <code>{HTTPStatus.S200_OK}</code> and any error occurred while the LLM is producing the output will be emitted in the response body or as an SSE data event.

Example code for printing errors in JavaScript:

```typescript
const request = await fetch('https://openrouter.ai/...');
console.log(request.status); // Will be an error code unless the model started processing your request
const response = await request.json();
console.error(response.error?.status); // Will be an error code
console.error(response.error?.message);
```

## Error Codes

- **{HTTPStatus.S400_Bad_Request}**: Bad Request (invalid or missing params, CORS)
- **{HTTPStatus.S401_Unauthorized}**: Invalid credentials (OAuth session expired, disabled/invalid API key)
- **{HTTPStatus.S402_Payment_Required}**: Your account or API key has insufficient credits. Add more credits and retry the request.
- **{HTTPStatus.S403_Forbidden}**: Your chosen model requires moderation and your input was flagged
- **{HTTPStatus.S408_Request_Timeout}**: Your request timed out
- **{HTTPStatus.S429_Too_Many_Requests}**: You are being rate limited
- **{HTTPStatus.S502_Bad_Gateway}**: Your chosen model is down or we received an invalid response from it
- **{HTTPStatus.S503_Service_Unavailable}**: There is no available model provider that meets your routing requirements

## Moderation Errors

If your input was flagged, the `error.metadata` will contain information about the issue. The shape of the metadata is as follows:

```typescript
type ModerationErrorMetadata = {
  reasons: string[]; // Why your input was flagged
  flagged_input: string; // The text segment that was flagged, limited to 100 characters. If the flagged input is longer than 100 characters, it will be truncated in the middle and replaced with ...
  provider_name: string; // The name of the provider that requested moderation
  model_slug: string;
};
```

## Provider Errors

If the model provider encounters an error, the `error.metadata` will contain information about the issue. The shape of the metadata is as follows:

```typescript
type ProviderErrorMetadata = {
  provider_name: string; // The name of the provider that encountered the error
  raw: unknown; // The raw error from the provider
};
```

## When No Content is Generated

Occasionally, the model may not generate any content. This typically occurs when:

- The model is warming up from a cold start
- The system is scaling up to handle more requests

Warm-up times usually range from a few seconds to a few minutes, depending on the model and provider.

If you encounter persistent no-content issues, consider implementing a simple retry mechanism or trying again with a different provider or model that has more recent activity.

Additionally, be aware that in some cases, you may still be charged for the prompt processing cost by the upstream provider, even if no content is generated.
