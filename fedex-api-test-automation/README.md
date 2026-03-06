# FedEx API Test Automation

## Profiles

Set `SPRING_PROFILES_ACTIVE` explicitly for every run (no default profile is set).

Examples (PowerShell):

```powershell
$env:SPRING_PROFILES_ACTIVE="local"
```

```powershell
$env:SPRING_PROFILES_ACTIVE="stage2"
```

```powershell
$env:SPRING_PROFILES_ACTIVE="stage3"
```

```powershell
$env:SPRING_PROFILES_ACTIVE="dev"
```

## Environment Validation

Validation is enabled by default in CI and non-local runs. You can control it with:

- `env.validation.enabled` (true/false)
- `env.validation.required` (comma-separated list of required keys, override per profile)

Override per profile using `application-{profile}.properties`.

Example (local):

```ini
env.validation.enabled=false
```

Example (custom required keys):

```ini
env.validation.required=TEST_ENV,MIRAKL_APIKEY,API_GATEWAY_CLIENT_ID
```

Example environment variables (PowerShell):

```powershell
$env:TEST_ENV="stage2"
$env:MIRAKL_APIKEY="<redacted>"
$env:API_GATEWAY_CLIENT_ID="<redacted>"
$env:MAGENTO_UI_CLIENT_ID="<redacted>"
$env:ADMIN_USERNAME="<redacted>"
$env:ADMIN_PASSWORD="<redacted>"
$env:SANDBOX_PUBLIC_APIKEY="<redacted>"
$env:SANDBOX_ENV_ID="<redacted>"
$env:CONFIGURATOR_ACCESS_TOKEN="<redacted>"
$env:AUTH_BROWSER_HEADLESS="true"
$env:AUTH_BROWSER_SLOWMO_MS="0"
```

## Test Resource Provider

- Non-local profiles use the classpath provider.
- The `local` profile uses filesystem paths and supports `test.resources.baseDir`.

Example:

```powershell
$env:SPRING_PROFILES_ACTIVE="local"
$env:TEST_RESOURCES_BASEDIR="C:\path\to\your"  # parent directory that contains testdata/
```

Example for the current layout:

```powershell
$env:SPRING_PROFILES_ACTIVE="local"
$env:TEST_RESOURCES_BASEDIR="C:\path\to\your\resources"
```

## CI Usage

Set the profile and required variables explicitly in your CI job environment. Example:

```powershell
$env:SPRING_PROFILES_ACTIVE="stage3"
$env:TEST_ENV="stage3"
$env:MIRAKL_APIKEY="<redacted>"
$env:API_GATEWAY_CLIENT_ID="<redacted>"
$env:MAGENTO_UI_CLIENT_ID="<redacted>"
$env:SANDBOX_PUBLIC_APIKEY="<redacted>"
$env:SANDBOX_ENV_ID="<redacted>"
```

## Running Tests

```powershell
mvn -q -DskipTests=false test
```
