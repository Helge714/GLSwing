package com.vingame.glswing.ui;

import java.awt.Color;

/** Editing coordinates for an sRGB color. Hue is measured in degrees. */
public enum GLColorModel {
	RGB("Red", "Green", "Blue"), HSV("Hue", "Saturation", "Value"),
	HSL("Hue", "Saturation", "Lightness"),
	OKLAB("Lightness", "A", "B"), OKLCH("Lightness", "Chroma", "Hue");
	private final String[] labels;
	GLColorModel(String... labels) { this.labels = labels; }
	/** Returns the user-facing name of a zero-based color channel. */
	public String channelName(int channel) { return labels[channel]; }
	/** Returns the supported editor minimum for a zero-based color channel. */
	public double minimum(int channel) { return this == OKLAB && channel > 0 ? -0.4 : 0; }
	/** Returns the supported editor maximum for a zero-based color channel. */
	public double maximum(int channel) {
		if (this == RGB) return 255;
		if ((this == HSV || this == HSL) && channel == 0 || this == OKLCH && channel == 2) return 360;
		if (this == OKLAB && channel > 0 || this == OKLCH && channel == 1) return 0.4;
		return 1;
	}
	/** Returns three editing coordinates; alpha is handled separately. */
	public double[] channels(Color color) {
		double r=color.getRed()/255d, g=color.getGreen()/255d, b=color.getBlue()/255d;
		if (this == RGB) return new double[] {color.getRed(),color.getGreen(),color.getBlue()};
		if (this == HSV || this == HSL) {
			float[] hsv=Color.RGBtoHSB(color.getRed(),color.getGreen(),color.getBlue(),null);
			if (this == HSV) return new double[] {hsv[0]*360,hsv[1],hsv[2]};
			double max=Math.max(r,Math.max(g,b)), min=Math.min(r,Math.min(g,b)), l=(max+min)/2;
			return new double[] {hsv[0]*360, max==min ? 0 : (max-min)/(1-Math.abs(2*l-1)),l};
		}
		r=linear(r); g=linear(g); b=linear(b);
		double l=Math.cbrt(.4122214708*r+.5363325363*g+.0514459929*b);
		double m=Math.cbrt(.2119034982*r+.6806995451*g+.1073969566*b);
		double s=Math.cbrt(.0883024619*r+.2817188376*g+.6299787005*b);
		double light=.2104542553*l+.7936177850*m-.0040720468*s;
		double a=1.9779984951*l-2.4285922050*m+.4505937099*s;
		double axisB=.0259040371*l+.7827717662*m-.8086757660*s;
		return this==OKLAB ? new double[] {light,a,axisB}
				: new double[] {light,Math.hypot(a,axisB),(Math.toDegrees(Math.atan2(axisB,a))+360)%360};
	}
	private static double linear(double value) { return value<=.04045 ? value/12.92 : Math.pow((value+.055)/1.055,2.4); }
	/** Converts editing coordinates to an sRGB color; out-of-gamut components are clipped. */
	public Color color(double[] channels, double alpha) {
		if (!Double.isFinite(alpha) || alpha < 0 || alpha > 1) throw new IllegalArgumentException("Normalized alpha required");
		double[] rgb = rgb(channels);
		return new Color(channel(rgb[0]),channel(rgb[1]),channel(rgb[2]),channel(alpha));
	}
	/** Tests whether the coordinates convert to sRGB without component clipping. */
	public boolean isInGamut(double[] channels) {
		for (double v : rgb(channels)) if (v < -0.00001 || v > 1.00001) return false;
		return true;
	}
	private static int channel(double v) { return (int)Math.round(Math.max(0,Math.min(1,v))*255); }
	private static double encoded(double v) { return v<=.0031308 ? 12.92*v : 1.055*Math.pow(v,1/2.4)-.055; }
	private double[] rgb(double[] c) {
		if (c == null || c.length != 3) throw new IllegalArgumentException("Three channels required");
		for (double v : c) if (!Double.isFinite(v)) throw new IllegalArgumentException("Finite channels required");
		if (this == RGB) return new double[] {c[0]/255,c[1]/255,c[2]/255};
		if (this == HSV) {
			int rgb=Color.HSBtoRGB((float)(c[0]/360),(float)c[1],(float)c[2]);
			return new double[] {(rgb>>16&255)/255d,(rgb>>8&255)/255d,(rgb&255)/255d};
		}
		if (this == HSL) {
			double[] rgb=new double[3]; double a=c[1]*Math.min(c[2],1-c[2]);
			for (int i=0;i<3;i++) { double k=((i==0?0:i==1?8:4)+c[0]/30)%12;
				rgb[i]=c[2]-a*Math.max(-1,Math.min(Math.min(k-3,9-k),1)); }
			return rgb;
		}
		double a=this==OKLAB?c[1]:c[1]*Math.cos(Math.toRadians(c[2]));
		double b=this==OKLAB?c[2]:c[1]*Math.sin(Math.toRadians(c[2]));
		double l=Math.pow(c[0]+.3963377774*a+.2158037573*b,3);
		double m=Math.pow(c[0]-.1055613458*a-.0638541728*b,3);
		double s=Math.pow(c[0]-.0894841775*a-1.2914855480*b,3);
		return new double[] {encoded(4.0767416621*l-3.3077115913*m+.2309699292*s),
				encoded(-1.2684380046*l+2.6097574011*m-.3413193965*s),
				encoded(-.0041960863*l-.7034186147*m+1.7076147010*s)};
	}
	/** A compact theme-compatible expression using the default numeric policy. */
	public String format(double[] c, double alpha) {
		return format(c, alpha, GLColorExpressionFormat.DEFAULT);
	}
	/** Formats a theme-compatible expression with an explicit numeric policy. */
	public String format(double[] c, double alpha, GLColorExpressionFormat format) {
		if (c == null || c.length != 3 || !Double.isFinite(alpha) || alpha<0 || alpha>1)
			throw new IllegalArgumentException("Three channels and normalized alpha required");
		for (double value : c) if (!Double.isFinite(value)) throw new IllegalArgumentException("Finite channels required");
		if (format == null) throw new IllegalArgumentException("Color expression format required");
		String alphaArgument = format.omitOpaqueAlpha() && alpha >= 1d - 0.5d / 255d
				? "" : ", " + format.bounded(alpha);
		return switch (this) {
		case RGB -> "rgb(" + rgb(c[0], format) + ", " + rgb(c[1], format)
				+ ", " + rgb(c[2], format) + alphaArgument + ")";
		case HSV, HSL -> name().toLowerCase(java.util.Locale.ROOT) + "("
				+ format.decimal(c[0]) + "deg, " + format.bounded(c[1]) + ", "
				+ format.bounded(c[2]) + alphaArgument + ")";
		case OKLAB -> "oklab(" + format.bounded(c[0]) + ", "
				+ format.decimal(c[1]) + "f, " + format.decimal(c[2]) + "f"
				+ alphaArgument + ")";
		case OKLCH -> "oklch(" + format.bounded(c[0]) + ", "
				+ format.decimal(c[1]) + "f, " + format.decimal(c[2]) + "deg"
				+ alphaArgument + ")";
		};
	}
	private static String rgb(double value, GLColorExpressionFormat format) {
		return format.channelNotation() == GLColorExpressionFormat.ChannelNotation.BYTE
				? Long.toString(Math.round(value)) : format.bounded(value / 255d);
	}
}
