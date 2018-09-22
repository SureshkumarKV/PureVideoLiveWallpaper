#version 300 es

in vec3 aVertCoord;
in vec2 aTexCoord;

uniform mat4 uModelMatrix;
uniform mat4 uViewMatrix;
uniform mat4 uProjMatrix;

out vec2 vTexCoord;

void main() {
	gl_Position = uProjMatrix * uViewMatrix * uModelMatrix * vec4(aVertCoord, 1.0);
    vTexCoord = aTexCoord;
}
