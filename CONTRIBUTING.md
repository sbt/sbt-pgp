
```
$ sbt
> library/publishSigned
> plugin/publishSigned
```



```
$ jenv shell 1.8
$ sbt
> ++2.12.2
> library/publishSigned
```

Uncomment

```scala
      // sbtVersion in pluginCrossBuild := "1.1.1",
      // scalaVersion := "2.12.4",
```

```
> reload
> plugin/publishSigned
```

Update /src/jekyll/index.md

