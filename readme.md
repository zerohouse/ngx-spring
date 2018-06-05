## Spring Controller -> Angular Api Service

To get a Git project into your build:

#### Step 1. Add the JitPack repository to your build file

    <repositories>
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
    </repositories>
#### Step 2. Add the dependency

	<dependency>
	    <groupId>com.github.zerohouse</groupId>
	    <artifactId>ngx-spring</artifactId>
	    <version>1.0.1</version>
	</dependency>
	
	
#### Make Api Service
    @Postcontstruct
    public void generate() {
            NgxGenerator ngxGenerator = new NgxGenerator("/api");
            ngxGenerator.excludeAdd(Entity.class);
            ngxGenerator.excludeAdd(Logged.class);
            ngxGenerator.excludeAdd(ExceptionHandleController.class);
            ngxGenerator.generate("com.abc", "./../../abc-client/src/app");
    }
