# syntax=docker/dockerfile:1
# .NET 은 단일 .cs 파일을 바로 빌드할 수 없어 프로젝트 골격이 필요하다.
# 골격과 NuGet 캐시를 미리 만들어 두어, 실행 시 네트워크 없이도 빌드된다.

FROM mcr.microsoft.com/dotnet/sdk:9.0-alpine

# 실행 시에는 텔레메트리·첫 실행 메시지가 표준 출력을 오염시키면 안 된다.
ENV DOTNET_CLI_TELEMETRY_OPTOUT=1 \
    DOTNET_NOLOGO=1 \
    DOTNET_SKIP_FIRST_TIME_EXPERIENCE=1 \
    DOTNET_CLI_HOME=/work \
    HOME=/work

# 프로젝트 골격을 만들고 한 번 복원해 둔다 (샌드박스는 네트워크가 차단돼 있다).
WORKDIR /skeleton
RUN printf '%s\n' \
    '<Project Sdk="Microsoft.NET.Sdk">' \
    '  <PropertyGroup>' \
    '    <OutputType>Exe</OutputType>' \
    '    <TargetFramework>net9.0</TargetFramework>' \
    '    <Nullable>disable</Nullable>' \
    '    <AssemblyName>app</AssemblyName>' \
    '    <RootNamespace>app</RootNamespace>' \
    '    <ImplicitUsings>disable</ImplicitUsings>' \
    '    <InvariantGlobalization>true</InvariantGlobalization>' \
    '  </PropertyGroup>' \
    '</Project>' > app.csproj \
    && printf 'class Warmup { static void Main() {} }\n' > Program.cs \
    && dotnet build -c Release -o /skeleton/out --nologo -v q \
    && rm -f Program.cs \
    && chmod -R a+rX /skeleton
