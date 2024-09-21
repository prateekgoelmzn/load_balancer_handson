To enable caching in Nginx when it acts as a reverse proxy or load balancer, you can configure Nginx to store and serve cached content. This helps reduce load on backend servers by serving frequently requested data directly from Nginx’s cache.

Here's a step-by-step guide on how to configure caching in Nginx:

### Step 1: Define the Cache Path

You need to define where Nginx will store cached content using the `proxy_cache_path` directive.

```nginx
http {
    # Define the cache path and settings
    proxy_cache_path /var/cache/nginx levels=1:2 keys_zone=my_cache:10m max_size=1g inactive=60m use_temp_path=off;

    # Upstream server (backend servers)
    upstream backend {
        server backend1.example.com;
        server backend2.example.com;
    }

    # Frontend server (where clients connect)
    server {
        listen 80;

        location / {
            # Forward requests to the backend
            proxy_pass http://backend;

            # Enable caching
            proxy_cache my_cache;
            proxy_cache_valid 200 10m;  # Cache 200 OK responses for 10 minutes
            proxy_cache_valid 404 1m;   # Cache 404 responses for 1 minute
            proxy_cache_use_stale error timeout updating;  # Use stale cache when backend errors
            proxy_cache_background_update on; # Asynchronously update cache
            add_header X-Cache-Status $upstream_cache_status; # Adds a header to show cache status
        }
    }
}
```

### Explanation:

- **`proxy_cache_path`**: Defines the location and settings for the cache:
    - `/var/cache/nginx`: Directory where cached content is stored.
    - `levels=1:2`: Directory hierarchy for storing cached files (helps manage a large number of cache files).
    - `keys_zone=my_cache:10m`: Creates a shared memory zone called `my_cache` to store cache keys and metadata (10 MB in this case).
    - `max_size=1g`: Sets the maximum size of the cache to 1 GB.
    - `inactive=60m`: Cached items that are not accessed for 60 minutes will be removed.
    - `use_temp_path=off`: Avoids using a temporary directory for cached files.

- **`proxy_pass`**: Forwards requests to the upstream backend (load-balanced servers).

- **`proxy_cache`**: Enables caching with the `my_cache` zone.

- **`proxy_cache_valid`**: Specifies how long different responses will be cached:
    - `200 10m`: Cache 200 (OK) responses for 10 minutes.
    - `404 1m`: Cache 404 (Not Found) responses for 1 minute.

- **`proxy_cache_use_stale`**: Allows Nginx to serve stale content if the backend server is unavailable (e.g., if there's an error or timeout).

- **`proxy_cache_background_update`**: Updates the cache in the background when a client requests stale content, so the next client gets fresh content.

- **`add_header X-Cache-Status $upstream_cache_status`**: Adds a custom header to the response to indicate cache status (`HIT`, `MISS`, `EXPIRED`).

### Step 2: Adjust Cache Duration for Other Status Codes

You can fine-tune caching for other HTTP status codes, depending on your use case.

**Example**:
```nginx
proxy_cache_valid 200 302 10m;   # Cache 200 OK and 302 Redirects for 10 minutes
proxy_cache_valid 404 1m;        # Cache 404 Not Found for 1 minute
proxy_cache_valid any 1m;        # Cache any other responses for 1 minute
```

### Step 3: Handle Cache Bypass (Optional)

You may want to bypass the cache in certain cases, such as when clients send a `Cache-Control: no-cache` header.

**Example**:
```nginx
location / {
    proxy_pass http://backend;
    
    # Enable caching
    proxy_cache my_cache;

    # Bypass cache if a no-cache header is present
    proxy_cache_bypass $http_cache_control;

    # Other cache settings
    proxy_cache_valid 200 10m;
    add_header X-Cache-Status $upstream_cache_status;
}
```

- **`proxy_cache_bypass $http_cache_control`**: Bypasses the cache when the `Cache-Control: no-cache` header is detected.

### Step 4: Monitor Cache Performance

To monitor whether caching is working correctly, you can check the `X-Cache-Status` header that was added earlier:

- **`HIT`**: The request was served from the cache.
- **`MISS`**: The request was forwarded to the backend server and a new response was cached.
- **`EXPIRED`**: The cached response was expired and a new one was fetched from the backend.
- **`STALE`**: A stale cached response was served due to backend unavailability.

### Step 5: Cache Purging (Optional)

If you want to purge specific cached content, you can use the `ngx_cache_purge` module. This module isn’t included by default, so you’ll need to install and compile it with Nginx.

**Example**:
```nginx
location /purge/ {
    allow 127.0.0.1;  # Allow requests from localhost
    deny all;         # Deny all other IPs
    proxy_cache_purge my_cache $uri;
}
```

This enables you to purge cached items by making a request to `/purge/` followed by the specific URL.

### Summary

- **Basic Caching Setup**: Use `proxy_cache_path` to define a cache zone and `proxy_cache` to enable caching for responses.
- **Cache Expiration**: Use `proxy_cache_valid` to define how long different types of responses should be cached.
- **Serve Stale Content**: Use `proxy_cache_use_stale` to serve cached content if the backend server is down or slow.
- **Cache Purging**: Optionally set up cache purging with a third-party module.

This setup reduces load on your backend servers by serving cached content directly from Nginx, improving both performance and reliability.


In your current setup, Nginx is likely caching responses for all users (clients) based on the URL `/uuid/get`, and since the cache is shared across requests, users are getting the same cached response even though they might expect different data. This is **not the desired behavior** when the response should vary depending on the request's origin (IP address) or any other unique factor such as query parameters, cookies, or headers.

This situation occurs because by default, Nginx caches responses based solely on the **URL**, and it doesn't take into account request-specific characteristics like IP addresses or other variables. For APIs where the response might differ for each client (e.g., unique responses for different users), you need to vary the cache key accordingly.

### To Fix This Behavior

You need to modify the caching logic so that the cache key includes additional request information, such as the client's IP address or headers. This can be done using the `proxy_cache_key` directive.

Here’s how to do that:

### 1. **Vary Cache by IP Address**

You can use the `$remote_addr` variable to vary the cache based on the client's IP address.

```nginx
http {
    proxy_cache_path /var/cache/nginx levels=1:2 keys_zone=my_cache:10m max_size=1g inactive=60m use_temp_path=off;

    server {
        listen 80;

        location /uuid/get {
            proxy_pass http://backend;

            # Enable caching with custom cache key
            proxy_cache my_cache;
            proxy_cache_key "$scheme$proxy_host$request_uri$remote_addr"; # Vary cache by client IP
            proxy_cache_valid 200 10m;
            proxy_cache_use_stale error timeout updating;
            add_header X-Cache-Status $upstream_cache_status;
        }
    }
}
```

- **`proxy_cache_key`**: This customizes the key that Nginx uses to cache responses. By including `$remote_addr` (the client's IP address), Nginx will cache responses separately for each client IP.
- **`$scheme$proxy_host$request_uri$remote_addr`**: This string constructs a unique key that combines the scheme (HTTP/HTTPS), proxy host, request URI, and client IP address.

### 2. **Vary Cache by Query String**

If your REST API depends on query parameters (e.g., `/uuid/get?user_id=123`), you may need to vary the cache based on the full request URL, including the query string.

```nginx
proxy_cache_key "$scheme$proxy_host$request_uri$is_args$args";
```

- **`$is_args$args`**: Includes the query string in the cache key.

### 3. **Vary Cache by Headers**

If your API behavior depends on specific headers (e.g., Authorization, Cookies, or custom headers), you can include those headers in the cache key.

**Example** (Vary cache based on `Authorization` header):
```nginx
proxy_cache_key "$scheme$proxy_host$request_uri$http_authorization";
```

- **`$http_authorization`**: Uses the `Authorization` header to distinguish cache entries.

### 4. **Bypass Cache for Specific Requests (Optional)**

If certain requests should always bypass the cache (e.g., authenticated users), you can configure `proxy_cache_bypass`.

```nginx
location /uuid/get {
    proxy_pass http://backend;

    # Bypass cache for requests with Authorization header (authenticated users)
    proxy_cache_bypass $http_authorization;

    # Enable caching for others
    proxy_cache my_cache;
    proxy_cache_key "$scheme$proxy_host$request_uri$remote_addr";
    proxy_cache_valid 200 10m;
    proxy_cache_use_stale error timeout updating;
    add_header X-Cache-Status $upstream_cache_status;
}
```

- **`proxy_cache_bypass $http_authorization`**: Skips the cache if the `Authorization` header is present, ensuring that authenticated users receive fresh data.

### 5. **Debugging Cache Behavior**

To verify how your cache behaves, you can use the custom header added (`X-Cache-Status`), which will tell you whether the response was served from the cache or fetched from the backend.

- **HIT**: Cached response.
- **MISS**: Backend response (newly cached).
- **STALE**: Stale cached response (backend was unavailable).

### Summary:
- **Issue**: By default, Nginx caches based only on the URL, which causes identical cached responses for all clients.
- **Solution**: Customize the cache key to include additional request-specific variables like client IP (`$remote_addr`), query parameters, or headers.
- **Goal**: Ensure that Nginx returns unique responses for each client based on their request characteristics, while still benefiting from caching.

Let me know if you need further customization!

While Nginx caching can be used for **both static content** and **REST APIs**, its effectiveness depends on the specific use case. Below, I’ll explain the use of caching for REST APIs and the best practices for doing so.

### 1. **Nginx Caching for Static Content**
Nginx is well-known for efficiently serving and caching static content, such as:
- HTML files
- CSS and JavaScript files
- Images and other media files
- Documents like PDFs

For static content, Nginx caching works great because:
- The content does not change often.
- The cache can serve many users without hitting the backend server.
- Caching static assets reduces server load and speeds up content delivery.

### 2. **Nginx Caching for REST APIs**
Caching REST API responses can also be beneficial, but it requires careful consideration. Here are the **pros and cons** and **best practices** for caching REST APIs:

#### Pros of Caching REST APIs:
- **Reduced Backend Load**: For high-traffic APIs that return similar responses for many users, caching can reduce the number of requests hitting your backend.
- **Improved Response Time**: Cached responses can be served directly from Nginx, reducing the latency for clients.
- **Scalability**: Nginx's caching mechanism scales well, especially for APIs serving large numbers of identical or similar responses.

#### Cons/Challenges of Caching REST APIs:
- **Dynamic Content**: REST APIs often serve dynamic data (personalized or frequently changing content). Caching can cause outdated or incorrect responses to be served.
- **Client-Specific Data**: APIs that serve data based on user identity, authentication, or custom headers (e.g., JWT tokens, sessions) can lead to incorrect cache hits for multiple users unless cache keys are carefully configured.
- **Stale Data**: There’s a risk of serving outdated data from the cache if cache invalidation isn’t handled well.

### 3. **Best Practices for Caching REST APIs**

If you decide to cache your REST API, follow these best practices to avoid issues:

#### 3.1. **Cache Safe and Idempotent Methods (GET)**
- **Cache Only `GET` Requests**: REST APIs often follow the principle that `GET` requests are idempotent (do not change state). Cache responses for `GET` requests only. Avoid caching `POST`, `PUT`, `DELETE`, or `PATCH` requests as these typically change data.

  ```nginx
  location /api/ {
      proxy_pass http://backend;
      proxy_cache my_cache;
      proxy_cache_methods GET; # Cache only GET requests
      proxy_cache_key "$scheme$proxy_host$request_uri$is_args$args"; # Cache based on URL + query params
  }
  ```

#### 3.2. **Use Conditional Caching**
- Use `Cache-Control` headers (e.g., `no-store`, `private`, `max-age`) to inform Nginx and clients about caching rules. Backend services should define whether a response is cacheable based on its content.

  **Example of setting cache-control headers:**
  ```nginx
  location /api/ {
      proxy_pass http://backend;

      # Cache GET responses for 10 minutes
      proxy_cache_valid 200 10m;

      # Pass through cache-control headers from backend
      proxy_ignore_headers Cache-Control;
  }
  ```

#### 3.3. **Avoid Caching Personalized or Dynamic Data**
- If the API response depends on authentication (e.g., `Authorization` headers or cookies), avoid caching or implement more advanced cache key configurations to vary the cache based on the `Authorization` header or cookie.

  ```nginx
  location /api/ {
      proxy_pass http://backend;

      # Enable caching, but vary by Authorization header
      proxy_cache_key "$scheme$proxy_host$request_uri$http_authorization";

      proxy_cache_valid 200 10m;
  }
  ```

#### 3.4. **Short Cache Expiration Times**
- Use short cache expiration times (e.g., 1 minute to 10 minutes) for API responses to reduce the risk of stale data being served.

  **Example:**
  ```nginx
  proxy_cache_valid 200 5m;  # Cache valid for 5 minutes
  ```

#### 3.5. **Invalidate Cache or Use Stale Cache**
- If the API response data changes frequently (e.g., product prices, user profiles), ensure cache invalidation is handled carefully. You can use `proxy_cache_use_stale` to serve stale content during backend failures.

  **Example:**
  ```nginx
  proxy_cache_use_stale error timeout updating;
  ```

- Additionally, you can manually invalidate or purge cache entries when data changes in the backend, though this requires more complex management.

#### 3.6. **Handle Query Parameters, Headers, or Cookies**
- If your API uses query parameters or headers for varying responses (e.g., filters or search queries), ensure that Nginx varies its cache key to include these values.

  **Example:**
  ```nginx
  proxy_cache_key "$scheme$proxy_host$request_uri$is_args$args"; # Include query params
  ```

#### 3.7. **Avoid Caching When Necessary**
- If certain requests should never be cached (e.g., requests with `Authorization` headers or sensitive data), use `proxy_cache_bypass` to skip the cache.

  **Example:**
  ```nginx
  proxy_cache_bypass $http_authorization;
  ```

### 4. **When Not to Cache REST APIs**
There are cases when you should avoid caching altogether:
- **Highly Personalized Responses**: If each response is unique to the user (e.g., user profile data), caching may not be appropriate.
- **Real-time APIs**: For APIs that return real-time or frequently changing data (e.g., live statistics or financial data), caching could result in outdated information being served.

### Summary of Best Practices

- **Cache GET Requests**: Cache only `GET` requests, as they are typically safe and idempotent.
- **Use Cache-Control Headers**: Let your backend decide when responses should be cached by setting appropriate `Cache-Control` headers.
- **Short Expiration Times**: Use short cache durations to minimize the risk of serving stale content.
- **Vary Cache by Client/Query**: Customize the cache key to include elements like query parameters, authentication headers, or client IP if responses vary based on these.
- **Avoid Caching for Dynamic/Personalized Content**: Don’t cache responses for authenticated users unless specifically necessary.
- **Manual Purge**: Consider implementing cache purging or invalidation mechanisms for cache consistency.

### Conclusion
Nginx caching is extremely powerful for **static content** and can be beneficial for certain REST API use cases, particularly for **public data** or **non-user-specific API responses**. However, it requires careful tuning to avoid serving incorrect or stale data for dynamic, personalized, or frequently changing APIs.

It's generally **best** to use caching for **publicly accessible, frequently requested, and stable API responses**, while avoiding it for user-specific or real-time data.


In Nginx, you can selectively enable or disable caching for different URLs or API endpoints using location blocks. Since you want to enable caching for `/uuid/get-slow` but not for `/uuid/get`, you can configure each location separately within the server block.

Here’s how you can do it:

### Example Configuration:
```nginx
http {
    # Define the cache path and settings
    proxy_cache_path /var/cache/nginx levels=1:2 keys_zone=my_cache:10m max_size=1g inactive=60m use_temp_path=off;

    server {
        listen 80;

        # Location block for /uuid/get (No caching)
        location /uuid/get {
            proxy_pass http://backend;

            # Disable caching for this specific endpoint
            proxy_no_cache 1;
            proxy_cache_bypass 1;
        }

        # Location block for /uuid/get-slow (Enable caching)
        location /uuid/get-slow {
            proxy_pass http://backend;

            # Enable caching
            proxy_cache my_cache;
            proxy_cache_valid 200 10m;  # Cache 200 OK responses for 10 minutes
            proxy_cache_use_stale error timeout updating;  # Use stale cache in case of errors
            add_header X-Cache-Status $upstream_cache_status;  # Add a custom header to show cache status
        }
    }
}
```

### Explanation:

1. **Cache Path Definition**:
  - **`proxy_cache_path`**: Defines where the cache will be stored and sets limits like cache size and expiration time. This is shared across all location blocks where caching is enabled.

2. **Location `/uuid/get` (No Cache)**:
  - **`proxy_pass http://backend`**: Forwards requests to the backend server.
  - **`proxy_no_cache 1;`**: Tells Nginx not to cache responses for this location.
  - **`proxy_cache_bypass 1;`**: Ensures that requests bypass the cache (even if there is a cache).

   Both `proxy_no_cache` and `proxy_cache_bypass` are set to `1`, ensuring no caching occurs for this endpoint.

3. **Location `/uuid/get-slow` (With Cache)**:
  - **`proxy_pass http://backend`**: Forwards requests to the backend server.
  - **`proxy_cache my_cache;`**: Enables caching using the defined `my_cache` zone.
  - **`proxy_cache_valid 200 10m;`**: Specifies that responses with a status code `200 OK` should be cached for 10 minutes.
  - **`proxy_cache_use_stale`**: Allows stale cached responses to be used in case of backend errors or timeouts.
  - **`add_header X-Cache-Status $upstream_cache_status;`**: Adds a custom header in the response to indicate the cache status (`HIT`, `MISS`, `STALE`).

### Cache Logic:

- **`/uuid/get`**: Caching is explicitly disabled using `proxy_no_cache` and `proxy_cache_bypass`.
- **`/uuid/get-slow`**: Caching is enabled for this location with a 10-minute cache duration for `200 OK` responses.

### Monitoring Cache Behavior:
To check if caching is working properly for `/uuid/get-slow`, you can use the `X-Cache-Status` header:
- **HIT**: The request was served from the cache.
- **MISS**: The response came from the backend and was cached.
- **STALE**: A stale cached response was served due to a backend issue.

### Conclusion:
By defining separate `location` blocks for each URL, you can control which API endpoints use caching and which do not. This is useful when some endpoints benefit from caching while others, especially those returning dynamic or frequently changing data, should bypass the cache.


The issue you are facing with the `404 Not Found` error is likely related to how Nginx handles the request path when using `proxy_pass`. Specifically, when you use `location` like `/api/v1/uuid/get`, Nginx looks for the exact match of the URI and then forwards the request to the upstream server. However, if the `proxy_pass` directive is not correctly set, Nginx might forward an incorrect path to the backend.

Here’s how you can fix it:

### Solution 1: Use Exact Location and Strip URI Path in `proxy_pass`

If you want Nginx to pass the exact URI `/api/v1/uuid/get` to the backend service without modifying it, you need to make sure that the `proxy_pass` directive passes the full request URI correctly.

If your backend server expects just `/uuid/get` (i.e., without `/api/v1`), you’ll need to rewrite or strip part of the URI.

#### Configuration Example:

```nginx
http {
    server {
        listen 9090;

        location /api/v1/uuid/get {
            # Strip /api/v1 from the URI when passing to the backend
            proxy_pass http://backend_server/uuid/get;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }
    }
}
```

### Explanation:

- **`location /api/v1/uuid/get`**: Matches requests with the path `/api/v1/uuid/get`.
- **`proxy_pass http://backend_server/uuid/get`**: The `proxy_pass` directive forwards the request to the backend, but it also rewrites the URI. It replaces `/api/v1/uuid/get` with `/uuid/get` before passing it to the upstream service.
- **`proxy_set_header`**: These directives ensure that the correct headers are passed along with the request, such as the original host and client IP.

### Solution 2: Retain Full Path in `proxy_pass`

If your backend expects the full URI `/api/v1/uuid/get`, you should not modify the URI in the `proxy_pass` directive.

#### Configuration Example:

```nginx
http {
    server {
        listen 9090;

        location /api/v1/uuid/get {
            # Pass the full URI to the backend
            proxy_pass http://backend_server;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }
    }
}
```

### Explanation:

- **`proxy_pass http://backend_server;`**: In this case, Nginx forwards the request to the backend server with the full original URI, which includes `/api/v1/uuid/get`.

### Common Pitfalls:
1. **Ensure the backend server URL is correct**: Make sure the `backend_server` is correctly defined and running on the expected port (e.g., `http://localhost:8080` or any relevant address).

2. **Exact URI Match**: If the backend server expects a specific URI structure, ensure that the path Nginx forwards matches that expectation.

### Testing and Debugging:

- **Check Nginx logs**: If you still encounter issues, check the Nginx error logs and access logs for more information:
  ```bash
  tail -f /var/log/nginx/error.log
  tail -f /var/log/nginx/access.log
  ```

- **Confirm Backend Connectivity**: Ensure that the backend service is reachable and working by directly accessing it via curl:
  ```bash
  curl http://backend_server/uuid/get
  ```

By setting up the `proxy_pass` correctly with the appropriate URI handling, your request should be properly forwarded, and you can avoid the `404 Not Found` error.