package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldablePropagation(t *testing.T) {
	rule := loadRule(t, "FoldablePropagation")
	e := newEnv(t)
	s, sc := e.scan("t", 1)
	alias := e.f.Metadata().AddColumn("f", types.Int)
	project := func(elem opt.ScalarExpr) memo.RelExpr {
		return e.f.ConstructProject(s, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(elem, alias)}, opt.MakeColSet(sc[0]))
	}
	before := e.f.ConstructSelect(project(e.num(7)), e.filters(e.f.ConstructEq(e.v(alias), e.v(sc[0]))))
	want := e.f.ConstructSelect(project(e.num(7)), e.filters(e.f.ConstructEq(e.num(7), e.v(sc[0]))))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(project(e.f.ConstructPlus(e.v(sc[0]), e.num(1))), e.filters(e.f.ConstructEq(e.v(alias), e.v(sc[0])))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(e.v(sc[0]), e.v(sc[0])))))
	e.expectNoMatch(rule, e.f.ConstructSelect(project(e.num(7)), e.filters(e.f.ConstructEq(e.v(sc[0]), e.v(sc[0])))))
}
