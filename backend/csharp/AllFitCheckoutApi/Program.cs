using System.Globalization;
using System.Text;
using Microsoft.AspNetCore.WebUtilities;
using Microsoft.AspNetCore.StaticFiles;
using Microsoft.Extensions.FileProviders;

var builder = WebApplication.CreateBuilder(args);
var publicRoot = Path.GetFullPath(Path.Combine(Directory.GetCurrentDirectory(), "public_html"));
if (!Directory.Exists(publicRoot))
{
    throw new DirectoryNotFoundException($"Website files were not found at {publicRoot}.");
}

builder.Services.AddCors(options =>
{
    options.AddPolicy("Local", policy =>
    {
        policy.SetIsOriginAllowed(origin =>
        {
            if (origin == "null")
            {
                return true;
            }

            return Uri.TryCreate(origin, UriKind.Absolute, out var uri)
                && (uri.Host.Equals("localhost", StringComparison.OrdinalIgnoreCase)
                    || uri.Host.Equals("127.0.0.1", StringComparison.OrdinalIgnoreCase))
                && (uri.Scheme == Uri.UriSchemeHttp || uri.Scheme == Uri.UriSchemeHttps);
        });
        policy.AllowAnyHeader();
        policy.AllowAnyMethod();
    });
});

var app = builder.Build();
app.UseCors("Local");

var contentTypes = new FileExtensionContentTypeProvider();
contentTypes.Mappings[".xhtml"] = "application/xhtml+xml";
app.UseStaticFiles(new StaticFileOptions
{
    FileProvider = new PhysicalFileProvider(publicRoot),
    ContentTypeProvider = contentTypes
});
app.MapGet("/", () => Results.Redirect("/catalogue.xhtml"));

var products = new Dictionary<int, decimal>
{
    [1] = 299m,
    [2] = 499m,
    [3] = 349m,
    [4] = 599m,
    [5] = 699m,
    [6] = 449m,
    [7] = 549m,
    [8] = 279m,
    [9] = 399m,
    [10] = 799m
};

for (var id = 11; id <= 100; id++)
{
    var priceInCents = id <= 42
        ? 17900L + ((id * 3749L) % 82000L)
        : id <= 71
            ? 22900L + ((id * 4287L) % 127100L)
            : 8900L + ((id * 2317L) % 51100L);
    products[id] = priceInCents / 100m;
}

app.MapMethods("/api/checkout", ["OPTIONS", "POST"], async context =>
{
    if (context.Request.Method == HttpMethod.Options.Method)
    {
        context.Response.StatusCode = StatusCodes.Status204NoContent;
        return;
    }

    if (context.Request.Method != HttpMethod.Post.Method)
    {
        context.Response.StatusCode = StatusCodes.Status405MethodNotAllowed;
        await context.Response.WriteAsJsonAsync(new { error = "POST is required." });
        return;
    }

    try
    {
        var body = await new StreamReader(context.Request.Body, Encoding.UTF8).ReadToEndAsync();
        var form = QueryHelpers.ParseQuery(body);

        GetRequired(form, "fullName", 120);
        string email = GetRequired(form, "email", 254);
        if (!email.Contains('@') || !email.Contains('.') || email.Contains(' '))
        {
            throw new InvalidOperationException("Enter a valid email address.");
        }

        GetRequired(form, "phone", 40);
        GetRequired(form, "address", 200);
        GetRequired(form, "city", 100);
        GetRequired(form, "postalCode", 20);

        if (!bool.TryParse(GetRequired(form, "mockPayment", 10).ToLowerInvariant(), out var mockPayment) || !mockPayment)
        {
            throw new InvalidOperationException("Mock payment approval was not requested.");
        }

        var discountCode = GetOptional(form, "discountCode").Trim().ToUpperInvariant();
        if (!string.IsNullOrEmpty(discountCode) && discountCode != "SAVE10")
        {
            throw new InvalidOperationException("The discount code is not valid.");
        }

        var shippingMethod = GetOptional(form, "shippingMethod").Trim().ToLowerInvariant();
        if (shippingMethod != "normal" && shippingMethod != "priority")
        {
            throw new InvalidOperationException("Select a valid shipping method.");
        }

        var itemValues = form["item"];
        if (itemValues.Count == 0 || itemValues.Count > 100)
        {
            throw new InvalidOperationException("Your order has no valid items.");
        }

        decimal subtotal = 0m;
        foreach (var itemValue in itemValues)
        {
            var itemParts = itemValue.Split(':', 2, StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries);
            if (itemParts.Length != 2)
            {
                throw new InvalidOperationException("An order item is invalid.");
            }

            if (!int.TryParse(itemParts[0], out var productId) || !int.TryParse(itemParts[1], out var quantity))
            {
                throw new InvalidOperationException("An order item is invalid.");
            }

            if (!products.TryGetValue(productId, out var unitPrice) || quantity < 1 || quantity > 99)
            {
                throw new InvalidOperationException("An order item is invalid.");
            }

            subtotal += unitPrice * quantity;
        }

        decimal discount = discountCode == "SAVE10"
            ? Math.Round(subtotal * 0.10m, 2, MidpointRounding.AwayFromZero)
            : 0m;

        decimal shipping = Math.Round((subtotal - discount) * 0.10m, 2, MidpointRounding.AwayFromZero);
        decimal priorityFee = shippingMethod == "priority" ? 15m : 0m;
        decimal total = subtotal - discount + shipping + priorityFee;

        var orderNumber = $"AF-{Guid.NewGuid():N}"[..8].ToUpperInvariant();
        var response = new
        {
            status = "MOCK_APPROVED",
            orderNumber,
            processedAt = DateTimeOffset.UtcNow.ToString("O"),
            subtotal = subtotal.ToString("0.00", CultureInfo.InvariantCulture),
            discount = discount.ToString("0.00", CultureInfo.InvariantCulture),
            shipping = shipping.ToString("0.00", CultureInfo.InvariantCulture),
            priorityFee = priorityFee.ToString("0.00", CultureInfo.InvariantCulture),
            shippingMethod,
            discountCode,
            total = total.ToString("0.00", CultureInfo.InvariantCulture)
        };

        context.Response.ContentType = "application/json; charset=utf-8";
        await context.Response.WriteAsJsonAsync(response);
    }
    catch (InvalidOperationException ex)
    {
        context.Response.StatusCode = StatusCodes.Status400BadRequest;
        await context.Response.WriteAsJsonAsync(new { error = ex.Message });
    }
    catch (Exception)
    {
        context.Response.StatusCode = StatusCodes.Status500InternalServerError;
        await context.Response.WriteAsJsonAsync(new { error = "The demo transaction could not be processed." });
    }
});

app.Run("http://127.0.0.1:5000");

static string GetRequired(Dictionary<string, Microsoft.Extensions.Primitives.StringValues> form, string key, int maxLength)
{
    var value = GetOptional(form, key);
    if (string.IsNullOrWhiteSpace(value) || value.Length > maxLength)
    {
        throw new InvalidOperationException($"Enter a valid {key}.");
    }

    return value.Trim();
}

static string GetOptional(Dictionary<string, Microsoft.Extensions.Primitives.StringValues> form, string key)
{
    return form.TryGetValue(key, out var value) ? value.ToString() : string.Empty;
}
