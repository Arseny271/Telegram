#version 300 es
precision highp float;

in vec2 outTexCoord;
in float outAlpha;
in float outLod;

out vec4 FragColor;

uniform vec2 viewportSize;
uniform vec4 imagePositionAndSize;

uniform sampler2D textureSampler;

uniform float pointSize;
uniform float progress;
uniform float density;

void main() {
    if (outLod > 0.5) {
        if (outAlpha < 0.01) {
            discard;
        }
        vec2 circCoord = gl_PointCoord * 2.0 - 1.0;
        float circDot = dot(circCoord, circCoord);
        if (circDot > 1.0) {
            discard;
        }
    }

    vec2 fixedTextureCoord = outTexCoord + ((gl_PointCoord - 0.5) * pointSize / (viewportSize * imagePositionAndSize.ba));
    if (any(lessThan(fixedTextureCoord, vec2(0.0))) || any(greaterThan(fixedTextureCoord, vec2(1.0)))) {
        discard;
    }

    vec4 textureColor = textureLod(textureSampler, fixedTextureCoord, outLod);
    FragColor = vec4(textureColor.rgb, textureColor.a * outAlpha);
}
