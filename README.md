# AndroidNumericText

## Project Overview

**AndroidNumericText** is a numeric input control library for Android applications, designed to provide convenient numeric input and formatting functionalities. It supports custom formats and styles, helping developers easily implement features related to numeric input.
![](Screen_recording.gif) ![](Gif_Demo.gif)

## Features

- **Support for Integer and Decimal Input**: Flexibly handle different types of numeric input.
- **Custom Numeric Formats**: Display formats like thousands separators, currency symbols, etc.
- **Input Restrictions**: Set maximum value, minimum value, and number of decimal places.
- **Simple and Easy-to-Use API**: Convenient for quick integration into projects.
- **Custom Styles and Themes**: Meet different UI design requirements.
- **iOS 17 SwiftUI contentTransition(.numericText()).

## Usage
Compose:

```kotlin
AnimatedNumber(number = number, modifier = Modifier.height(45.dp))
```
### Add the Control in Layout File

```xml
<tech.hezy.numerictext.AnimatedNumberView
        android:id="@+id/animatedNumberView"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        app:textSize="40sp"
        app:textColor="@color/black"
        app:layout_constraintBottom_toTopOf="@id/buttonIncrease"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />
```

### Configure in Code

```kotlin
val animatedNumberView = findViewById<AnimatedNumberView>(R.id.animatedNumberView)
        animatedNumberView.setNumber(currentNumber)
```

## Contribution Guidelines

We welcome feedback and suggestions for this project. You can contribute by submitting Issues or Pull Requests. Please refer to the [CONTRIBUTING.md](CONTRIBUTING.md) file for detailed contribution guidelines.

## License

This project is licensed under the MIT License. For more information, please see the [LICENSE](LICENSE) file.

## Contact

If you have any questions or suggestions, please contact the project maintainer:

- **Email**: he0564@gmail.com
- **GitHub**: [ZionHeZY](https://github.com/ZionHeZY)

## Acknowledgments

Thank you to all the developers and users who have contributed to this project.
