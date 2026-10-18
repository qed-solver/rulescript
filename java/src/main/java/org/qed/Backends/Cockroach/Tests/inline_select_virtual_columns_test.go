package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestInlineSelectVirtualColumns(t *testing.T) {
	rule := loadRule(t, "InlineSelectVirtualColumns")
	e := newEnv(t)
	scan, sc := e.scan("t", 2)
	a, b := sc[0], sc[1]
	fc := e.f.Metadata().AddColumn("f", types.Bool)
	gc := e.f.Metadata().AddColumn("g", types.Bool)
	passthrough := opt.MakeColSet(a, b)
	fExpr := e.f.ConstructGt(e.v(a), e.v(b))
	gExpr := e.f.ConstructLt(e.v(a), e.v(b))
	projections := memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(fExpr, fc),
		e.f.ConstructProjectionsItem(gExpr, gc),
	}
	project := e.f.ConstructProject(scan, projections, passthrough)
	before := e.f.ConstructSelect(project, e.filters(
		e.f.ConstructNot(e.v(fc)),
		e.f.ConstructNot(e.v(gc)),
	))
	inlined := e.f.ConstructSelect(scan, e.filters(e.f.ConstructNot(fExpr)))
	after := e.f.ConstructSelect(
		e.f.ConstructProject(inlined, projections, passthrough),
		e.filters(e.f.ConstructNot(e.v(gc))),
	)
	e.expectRewrite(rule, before, after)

	e.expectNoMatch(rule, e.f.ConstructSelect(scan, e.filters(
		e.f.ConstructNot(fExpr),
		e.f.ConstructNot(gExpr),
	)))
	e.expectNoMatch(rule, e.f.ConstructSelect(
		e.f.ConstructProject(scan, projections, opt.MakeColSet(a)),
		e.filters(e.f.ConstructNot(e.v(fc)), e.f.ConstructNot(e.v(gc))),
	))
	wide, wc := e.scan("t", 3)
	wideF := e.f.Metadata().AddColumn("wf", types.Bool)
	wideG := e.f.Metadata().AddColumn("wg", types.Bool)
	wideProjections := memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructGt(e.v(wc[0]), e.v(wc[1])), wideF),
		e.f.ConstructProjectionsItem(e.f.ConstructLt(e.v(wc[0]), e.v(wc[1])), wideG),
	}
	wideBefore := e.f.ConstructSelect(
		e.f.ConstructProject(wide, wideProjections, opt.MakeColSet(wc[0], wc[1])),
		e.filters(e.f.ConstructNot(e.v(wideF)), e.f.ConstructNot(e.v(wideG))),
	)
	e.expectNoMatch(rule, wideBefore)
}
