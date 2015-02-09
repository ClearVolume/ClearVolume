#version 400 core
 
layout(location = 0) in vec3 vertexPosition;
layout(location = 1) in vec3 vertexNormal;
layout(location = 2) in vec2 vertexTexCoord;

out VertexData {
    vec3 Position;
    vec3 Normal;
    vec2 TexCoord;
} VertexOut;

out vec4 color;

uniform mat4 modelview;
uniform mat4 projection;

uniform int vertexCount;


void main()
{
   VertexOut.Normal = vertexNormal;
   VertexOut.Position = vec3(modelview*vec4(vertexPosition, 1.0));
   VertexOut.TexCoord = vertexTexCoord;

    gl_Position = (projection*modelview)*vec4(vertexPosition , 1.0);

   if(gl_VertexID == 0) {
        color = vec4(0.0, 1.0, 0.0, 1.0);
        return;
   } if(gl_VertexID == vertexCount-1) {
        color = vec4(1.0, 0.0, 0.0, 1.0);
        return;
   } else {
        color = vec4(1.0, 1.0, 1.0, 0.5);
        return;
   }


}


