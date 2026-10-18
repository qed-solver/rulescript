package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldNullUnary(t *testing.T) {
	rule := loadRule(t, "FoldNullUnary")
	e := newEnv(t)
	s, sc := e.scan("t", 1)
	out := e.f.Metadata().AddColumn("neg", types.Int)
	nullInt := func() opt.ScalarExpr { return e.f.ConstructNull(types.Int) }
	one := func(elem opt.ScalarExpr) memo.ProjectionsExpr {
		return memo.ProjectionsExpr{e.f.ConstructProjectionsItem(elem, out)}
	}
	negNull := &memo.UnaryMinusExpr{Input: nullInt(), Typ: types.Int}
	before := e.f.ConstructProject(s, one(negNull), opt.ColSet{})
	want := e.f.ConstructProject(s, one(nullInt()), opt.ColSet{})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructProject(s, one(e.f.ConstructUnaryMinus(e.v(sc[0]))), opt.ColSet{}))
	e.expectNoMatch(rule, e.f.ConstructProject(s, one(&memo.UnaryComplementExpr{Input: nullInt(), Typ: types.Int}), opt.ColSet{}))
}
