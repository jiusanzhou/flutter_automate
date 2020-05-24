import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';

class MTBButton extends StatelessWidget {
  const MTBButton({
    Key key,
    this.text: "",
    this.height: 48.0,
    this.textSize: 14,
    this.width: double.infinity,
    this.flat: true,
    this.color,
    this.textColor: Colors.white,
    this.loading: false,
    this.borderd: true,
    @required this.onPressed,
  }) : super(key: key);

  final String text;
  final double textSize;
  final VoidCallback onPressed;
  final double height;
  final double width;
  final bool flat;
  final Color color;
  final Color textColor;
  final bool loading;
  final bool borderd;

  @override
  Widget build(BuildContext context) {
    return FlatButton(
      onPressed: onPressed,
      textColor: this.textColor,
      color: this.color ?? Theme.of(context).primaryColor,
      disabledTextColor: Colors.black54,
      disabledColor: Colors.grey[400],
      shape: borderd?RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(9999)):null,
      child: _button(),
    );
  }

  Widget _button() {
    return loading
          ? CupertinoActivityIndicator(radius: 16.0)
          : Text(text, style: TextStyle(fontSize: textSize));
    // return Container(
    //   height: height,
    //   width: width,
    //   alignment: Alignment.center,
    //   child: loading
    //       ? CupertinoActivityIndicator(radius: 16.0)
    //       : Text(text, style: TextStyle(fontSize: textSize)),
    // );
  }

  // Widget _button() {
  //   return Column(
  //     children: <Widget>[
  //       Container(
  //         height: height,
  //         width: double.infinity,
  //         alignment: Alignment.center,
  //         child: loading
  //             ? CupertinoActivityIndicator(radius: 16.0)
  //             : Text(
  //                 text,
  //                 style: TextStyle(fontSize: Dimens.font_sp18),
  //               ),
  //       ),
  //     ],
  //   );
  // }
}
