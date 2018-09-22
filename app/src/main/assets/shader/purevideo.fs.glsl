#version 300 es

#extension GL_OES_EGL_image_external : require
#extension GL_OES_EGL_image_external_essl3 : require

precision mediump float;

uniform samplerExternalOES uTextureExternalOES;

in vec2 vTexCoord;

out vec4 fragColor;

void main() {
	fragColor = texture(uTextureExternalOES, vTexCoord);
}
