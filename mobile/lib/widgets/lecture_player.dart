import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:youtube_player_iframe/youtube_player_iframe.dart';

import '../video/youtube_video_id.dart';

/// In-app YouTube surface for a lecture. Uses the API `video_ref` only inside this widget.
class LecturePlayer extends StatefulWidget {
  const LecturePlayer({required this.videoRef, super.key});

  final String videoRef;

  @override
  State<LecturePlayer> createState() => _LecturePlayerState();
}

class _LecturePlayerState extends State<LecturePlayer> {
  YoutubePlayerController? _controller;

  @override
  void initState() {
    super.initState();
    if (_useLivePlayer) {
      final videoId = youtubeVideoId(widget.videoRef);
      if (videoId != null) {
        _controller = YoutubePlayerController.fromVideoId(
          videoId: videoId,
          autoPlay: false,
          params: const YoutubePlayerParams(showFullscreenButton: true),
        );
      }
    }
  }

  bool get _useLivePlayer {
    if (kIsWeb) {
      return true;
    }
    final binding = WidgetsBinding.instance;
    return !binding.runtimeType.toString().contains('TestWidgets');
  }

  @override
  void dispose() {
    _controller?.close();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AspectRatio(
      key: const Key('lecture-player'),
      aspectRatio: 16 / 9,
      child: _controller == null
          ? ColoredBox(
              color: Colors.black,
              child: Center(
                child: Icon(Icons.play_circle_outline, color: Colors.white, size: 64),
              ),
            )
          : YoutubePlayer(controller: _controller!, aspectRatio: 16 / 9),
    );
  }
}
