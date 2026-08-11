#!/usr/bin/env python3
"""Replica la aritmetica de RecipeBanScreen.init() y busca solapes.

Cada elemento se declara como (nombre, x, y, w, h). Los textos ocupan 8px de alto.
Se comprueba que nada se pise y que todo quede dentro del panel.
"""

HEADER = 36
FOOTER = 8
ROW = 18
BOTTOM_BLOCK = 87


def layout(width, height):
    panelW = min(width - 16, 500)
    panelH = min(height - 16, 340)
    leftPos = (width - panelW) // 2
    topPos = (height - panelH) // 2

    x = leftPos + 8
    y = topPos + HEADER
    bodyW = panelW - 16
    bodyH = panelH - HEADER - FOOTER
    colW = (bodyW - 8) // 2
    rightX = x + colW + 8
    halfW = (colW - 2) // 2

    listY = y + 38
    listH = max(2 * ROW, bodyH - 38 - BOTTOM_BLOCK)
    selY = listY + listH + 3
    actionY = selY + 11
    r1 = y + bodyH - 53
    r2 = y + bodyH - 36
    r3 = y + bodyH - 19

    actionW = (bodyW - 8) // 3
    bw = colW // 3 - 2

    # Anchos reales de texto: 6px por caracter, ya sin los codigos de color.
    # Se aplica el mismo recorte que hace el metodo fit() del codigo.
    titulo = '* Fantastic Recipes - 15353 items - 12 recetas - 34 items'
    ayuda = ('Baneo de receta: no se puede craftear.  Baneo de item: no se puede ni tener.'
             if panelW >= 470 else 'receta = no se craftea - item = no se puede tener')
    seleccion = 'Seleccionado: Netherite-Diamond Axe - ahora: Item completo'

    items = [
        ('titulo(texto)', leftPos + 8, topPos + 5, min(len(titulo) * 6, panelW - 32), 8),
        ('ayuda(texto)', leftPos + 8, topPos + 21, min(len(ayuda) * 6, panelW - 16), 8),
        ('cerrarX', leftPos + panelW - 18, topPos + 2, 14, 14),
        ('btn.catalogo', x, y, colW, 16),
        ('btn.baneados', rightX, y, colW, 16),
        ('buscar.izq', x, y + 18, colW, 16),
        ('buscar.der', rightX, y + 18, colW, 16),
        ('lista.izq', x, listY, colW, listH),
        ('lista.der', rightX, listY, colW, listH),
        ('seleccion(texto)', leftPos + 10, selY, min(len(seleccion) * 6, panelW - 20), 8),
        ('accion.receta', x, actionY, actionW, 18),
        ('accion.item', x + actionW + 4, actionY, actionW, 18),
        ('accion.desbanear', x + 2 * (actionW + 4), actionY, bodyW - 2 * (actionW + 4), 18),
        ('lbl.limpiezas(texto)', rightX + 2, r1 + 4, colW, 8),
        ('quitar.recetas', rightX, r2, halfW, 16),
        ('quitar.items', rightX + halfW + 2, r2, colW - halfW - 2, 16),
        ('desbanear.todo', rightX, r3, colW, 16),
    ]

    for row_index, row_y in enumerate((r1, r2, r3)):
        for col in range(3):
            items.append(('cat%d%d' % (row_index, col), x + col * (bw + 2), row_y, bw, 16))

    return items, (leftPos, topPos, panelW, panelH), listH // ROW


def overlaps(a, b):
    _, ax, ay, aw, ah = a
    _, bx, by, bw_, bh = b
    return ax < bx + bw_ and bx < ax + aw and ay < by + bh and by < ay + ah


def check(width, height):
    items, panel, rows = layout(width, height)
    leftPos, topPos, panelW, panelH = panel
    problems = []

    for i in range(len(items)):
        for j in range(i + 1, len(items)):
            if overlaps(items[i], items[j]):
                problems.append('SOLAPE %s <-> %s' % (items[i][0], items[j][0]))

    for name, ix, iy, iw, ih in items:
        if ix < leftPos or ix + iw > leftPos + panelW:
            problems.append('FUERA-H %s' % name)
        if iy < topPos or iy + ih > topPos + panelH:
            problems.append('FUERA-V %s (y=%d..%d, panel=%d..%d)'
                            % (name, iy, iy + ih, topPos, topPos + panelH))

    status = 'OK  ' if not problems else 'FALLA'
    print('%s %4dx%-4d panel %dx%d  filas de lista: %d' % (status, width, height, panelW, panelH, rows))
    for p in sorted(set(problems)):
        print('        ', p)
    return not problems


ok = True
# La resolucion de la captura del usuario (1024x560 a escala 2) y otras habituales.
for w, h in [(512, 280), (480, 270), (640, 360), (854, 480), (427, 240), (400, 220), (320, 240)]:
    ok &= check(w, h)

print()
print('TODO BIEN' if ok else 'HAY PROBLEMAS')
