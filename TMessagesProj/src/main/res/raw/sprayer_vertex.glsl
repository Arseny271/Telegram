#version 300 es

layout (location = 0) in vec2 inPosistion;
layout(location = 1) in vec3 inNoise;
layout(location = 2) in vec4 inRandom1;
layout(location = 3) in vec4 inRandom2;
layout(location = 4) in vec4 inRandom3;

out vec3 feedbackNoise;
out vec4 feedbackRandom1;
out vec4 feedbackRandom2;
out vec4 feedbackRandom3;

out vec2 outTexCoord;
out float outAlpha;
out float outLod;

uniform vec2 viewportSize;
uniform vec4 imagePositionAndSize;

uniform float pointSize;
uniform float progress;
uniform float density;
uniform float reset;  // -1 true or 0 false

float rand(vec2 n, float seed) {
    return fract(sin(dot(n,vec2(12.9898,4.1414-seed*.42)))*4375.5453);
}

float smoothNoise(vec2 st, float seed) {
    vec2 i = floor(st);
    vec2 f = fract(st);

    float a = rand(i, seed);
    float b = rand(i + vec2(1.0, 0.0), seed);
    float c = rand(i + vec2(0.0, 1.0), seed);
    float d = rand(i + vec2(1.0, 1.0), seed);

    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float noise(vec2 st, int octaves, float seed) {
    float total = 0.0;
    float persistence = 0.5;

    for (int i = 0; i < octaves; i++) {
        float frequency = pow(2.0, float(i));
        float amplitude = pow(persistence, float(octaves - i));
        total += smoothNoise(st * frequency, seed) * amplitude;
    }

    return total;
}

float wave (float x, float p, float k) {
    return 1.0 - ((x - p * (1.0 + k) + k) / k);
}

float wave (float x, float p, float k, float w_start, float w_end) {
    return wave(x, (p - w_start) / (w_end - w_start), k);
}

vec2 normalizePosition (vec2 pos) {         // from [-1 .. 1] to [0 .. 1];      // left-top is 0
    return vec2(1.0 + pos.x, 1.0 - pos.y) / 2.0;
}

vec2 denormalizePosition (vec2 pos) {       // left-bottom is -1 -1
    return (pos * 2.0 - 1.0) * vec2(1.0, -1.0);
}

vec2 applyMatrix (vec2 point, vec2 center, mat2 rotationMatrix) {
    vec2 diff = point - center;
    return center + rotationMatrix * diff;
}

#define DISTANCE_TO_SCALE_CENTER 20.0
#define SCALE_CENTER_RANDOMIZE 50.0

void main() {
    vec2 viewportSizeDp = viewportSize / density;
    vec2 normalizedPosition = normalizePosition(inPosistion);
    vec2 normalizedImagePosition = (normalizedPosition - imagePositionAndSize.rg) / imagePositionAndSize.ba;
    vec2 denormalizedImagePosition = denormalizePosition(normalizedImagePosition);

    if (reset < -0.5) {
        float seed = 0.25242;
        feedbackNoise = vec3(
            noise(normalizedPosition * (viewportSizeDp / 256.0), 5, 0.8243),
            noise(normalizedPosition * (viewportSizeDp / 256.0), 5, 0.3482),
            noise(normalizedPosition * (viewportSizeDp / 48.0), 8, 0.7482)
        );
        feedbackRandom1 = vec4(
            rand(normalizedImagePosition, seed + 1.0 / 15.0),
            rand(normalizedImagePosition, seed + 2.0 / 15.0),
            rand(normalizedImagePosition, seed + 3.0 / 15.0),
            rand(normalizedImagePosition, seed + 4.0 / 15.0)
        );
        feedbackRandom2 = vec4(
            rand(normalizedImagePosition, seed + 5.0 / 15.0),
            rand(normalizedImagePosition, seed + 6.0 / 15.0),
            rand(normalizedImagePosition, seed + 7.0 / 15.0),
            rand(normalizedImagePosition, seed + 8.0 / 15.0)
        );
        feedbackRandom3 = vec4(
            rand(normalizedImagePosition, seed + 9.0 / 15.0),
            rand(normalizedImagePosition, seed + 10.0 / 15.0),
            rand(normalizedImagePosition, seed + 11.0 / 15.0),
            rand(normalizedImagePosition, seed + 12.0 / 15.0)
        );
    } else {
        feedbackNoise = inNoise;
        feedbackRandom1 = inRandom1;
        feedbackRandom2 = inRandom2;
        feedbackRandom3 = inRandom3;
    }

    vec2 dpPosition = normalizedPosition * viewportSizeDp;
    vec2 dpImageSize = viewportSizeDp * imagePositionAndSize.ba;

    vec2 dpGlobalCenter = viewportSizeDp * (imagePositionAndSize.rg + imagePositionAndSize.ba / 2.0);
    vec2 dpScaleCenter = dpPosition + normalize((dpGlobalCenter - dpPosition) + (feedbackRandom1.rg * 2.0 - 1.0) * SCALE_CENTER_RANDOMIZE) * DISTANCE_TO_SCALE_CENTER;

    float distanceToRotateCenter = min(dpImageSize.x, dpImageSize.y) * 0.12 * ((3.0 + feedbackRandom2.r) / 4.0);
    vec2 dpRotateCenter = dpPosition + (feedbackNoise.xy * 2.0 - 1.0) * distanceToRotateCenter;

    /* Waves */

    float waveFirsrDestruction = clamp(wave(normalizedImagePosition.x, progress, 0.1, 0.0, 0.25), 0.0, 1.0);
    float waveDistortion       = max(progress * 6.0 - normalizedImagePosition.x * 1.5, 0.0);                                        // up to 6
    float waveAttenuation      = clamp(wave(normalizedImagePosition.x, progress, 1.5, 0.2, 1.0), 0.0, 1.0);
    float alphaThreshold = 0.35 * waveFirsrDestruction + 0.35 * waveAttenuation + 0.15 * max((progress - 0.8) * 5.0, 0.0);

    /* Gravity */

    float waveSecondGravitySpeed = feedbackRandom2.g;
    float waveFirsrGravity  = max(wave(normalizedImagePosition.x, progress, 3.4, 0.05, 0.75), 0.0);                                 // up to ~1.75
    float waveSecondGravity = max(wave(normalizedImagePosition.x, progress, 7.5, 0.5, 1.0 - waveSecondGravitySpeed * 0.15), 0.0);   // up to ~1.61
    vec2 dpGravityOffset = vec2(0.0, -(waveFirsrGravity * 10.0 + waveSecondGravity * 18.0 * (0.8 + normalizedImagePosition.y * 0.4)));

    vec2 dpOffsetStart = vec2(pointSize / density * waveFirsrDestruction) * feedbackRandom1.ba;

    /**/





    float attenuation = clamp(progress * 3.0 - 1.0 - normalizedImagePosition.x, 0.0, 1.0);
    float distortion = clamp(progress * 2.0 - normalizedImagePosition.x / 2.0, 0.0, 1.0) + attenuation * 6.0;

    float windSpeed = (2.0 + feedbackRandom2.b) / 3.0;
    float wind = max(progress * distortion - 0.1 * (1.0 - progress), 0.0) * 2.0 * windSpeed;

    vec2 positionOffsetDp = vec2(
        (waveDistortion + wind) * denormalizedImagePosition.x * 0.5 + wind * 0.25,
        waveDistortion * denormalizedImagePosition.y
    );

    float scaleForScaleC = feedbackRandom2.a;
    float scaleDelayC = feedbackRandom3.r;
    float scale = 1.0 + waveFirsrDestruction * 0.1 + max(((waveDistortion - scaleDelayC * 2.0) / 2.0) * scaleForScaleC, 0.0);

    float rotationTarget = ((feedbackRandom3.g - 0.5) * 2.0) * clamp(scale - 1.4142, 0.0, 1.0);
    float angle =  waveDistortion / 4.5 + rotationTarget * waveDistortion; // * (feedbackRandom3.b * 2.0 - 1.0);






    /* * */

    vec2 offsetDp = dpOffsetStart + dpGravityOffset + positionOffsetDp;

    mat2 scaleMatrix = mat2(scale, 0.0 ,0.0, scale);
    mat2 rotationMatrix = mat2(cos(angle), sin(angle), -sin(angle), cos(angle));

    vec2 resultPositionDpS = applyMatrix(dpPosition, dpScaleCenter, scaleMatrix);
    vec2 resultPositionDp = applyMatrix(resultPositionDpS, dpRotateCenter, rotationMatrix);

    vec2 resultNormalizedPosition = (offsetDp + resultPositionDp) / viewportSizeDp;
    vec2 resultDenormalizedPosition = denormalizePosition(resultNormalizedPosition);

    outTexCoord = normalizedImagePosition;
    outAlpha = clamp(1.0 - wave(feedbackNoise.z, alphaThreshold, 0.1), 0.0, 1.0);
    outLod = waveFirsrDestruction > 0.99 ? 3.0 : 0.0;

    gl_PointSize = outAlpha > 0.01 ? pointSize : 1.0;
    gl_Position = vec4(resultDenormalizedPosition, 0.0, 1.0);
}
