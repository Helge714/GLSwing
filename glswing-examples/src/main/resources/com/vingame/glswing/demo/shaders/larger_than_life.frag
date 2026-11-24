#version 330 core

in vec2 vTexCoord;
in vec4 vColor;

uniform sampler2D uTexture;
uniform vec2 uTexelSize;
uniform int uRadius;
uniform int uNeighborhood;
uniform int uIncludeCenter;
uniform int uBirthMin;
uniform int uBirthMax;
uniform int uSurvivalMin;
uniform int uSurvivalMax;

out vec4 fragColor;

const int MAX_RADIUS = 5;

bool belongsToNeighborhood(int x, int y) {
    if (abs(x) > uRadius || abs(y) > uRadius)
        return false;
    if (uNeighborhood == 1)
        return abs(x) + abs(y) <= uRadius;
    if (uNeighborhood == 2)
        return x * x + y * y <= uRadius * uRadius;
    return true;
}

int aliveAt(int x, int y) {
    vec2 offset = vec2(float(x), float(y)) * uTexelSize;
    return texture(uTexture, vTexCoord + offset).r >= 0.5 ? 1 : 0;
}

void main() {
    int count = 0;
    for (int y = -MAX_RADIUS; y <= MAX_RADIUS; y++) {
        for (int x = -MAX_RADIUS; x <= MAX_RADIUS; x++) {
            if (!belongsToNeighborhood(x, y))
                continue;
            if (uIncludeCenter == 0 && x == 0 && y == 0)
                continue;
            count += aliveAt(x, y);
        }
    }

    bool current = aliveAt(0, 0) != 0;
    bool survives = count >= uSurvivalMin && count <= uSurvivalMax;
    bool born = count >= uBirthMin && count <= uBirthMax;
    float next = current ? (survives ? 1.0 : 0.0) : (born ? 1.0 : 0.0);
    fragColor = vec4(vec3(next), 1.0) * vColor;
}
