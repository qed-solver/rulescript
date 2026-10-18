package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFilterProjectTranspose(t *testing.T) {
	rule := loadRule(t, "FilterProjectTranspose")
	e := newEnv(t)
	s, sc := e.scan("t", 3)
	out := e.f.Metadata().AddColumn("p", types.Int)

	projPlus := e.f.ConstructPlus(e.v(sc[0]), e.num(1))
	proj := e.f.ConstructProject(s,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(projPlus, out)},
		opt.ColSet{})
	before := e.f.ConstructSelect(proj, e.filters(e.f.ConstructGt(e.v(out), e.num(5))))

	pushedPlus := e.f.ConstructPlus(e.v(sc[0]), e.num(1))
	want := e.f.ConstructProject(
		e.f.ConstructSelect(s, e.filters(e.f.ConstructGt(pushedPlus, e.num(5)))),
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(sc[0]), e.num(1)), out)},
		opt.ColSet{})
	e.expectRewrite(rule, before, want)

	twoOut := e.f.Metadata().AddColumn("q", types.Int)
	wide := e.f.ConstructProject(s,
		memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(sc[0]), e.num(1)), out),
			e.f.ConstructProjectionsItem(e.v(sc[2]), twoOut),
		},
		opt.ColSet{})
	e.expectNoMatch(rule, e.f.ConstructSelect(wide, e.filters(e.f.ConstructGt(e.v(out), e.num(5)))))

	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructGt(e.v(sc[1]), e.num(5)))))
}
