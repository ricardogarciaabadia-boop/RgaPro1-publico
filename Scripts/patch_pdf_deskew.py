from pathlib import Path
P=Path('app/src/main/java/com/rgapro1/ocaso/PdfOcrHelper.java')
s=P.read_text(encoding='utf-8')
old='source = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);\n                                page.render(source, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);'
new='source = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);\n                                page.render(source, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);\n                                Bitmap straightened = DniImagePreprocessor.deskew(source);\n                                if (straightened != source) { source.recycle(); source = straightened; }'
if old in s:s=s.replace(old,new,1)
P.write_text(s,encoding='utf-8')
print('PDF pages now receive automatic deskew before OCR')
