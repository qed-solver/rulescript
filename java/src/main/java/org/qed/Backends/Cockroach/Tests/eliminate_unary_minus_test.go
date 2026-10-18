package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestEliminateUnaryMinus(t *testing.T) {
	rule := loadRule(t, "EliminateUnaryMinus")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	neg := func(x opt.ScalarExpr) opt.ScalarExpr { return e.f.ConstructUnaryMinus(x) }
	out := e.f.Metadata().AddColumn("neg", types.Int)
	items := func(x opt.ScalarExpr) memo.ProjectionsExpr {
		return memo.ProjectionsExpr{e.f.ConstructProjectionsItem(x, out)}
	}
	before := e.f.ConstructProject(s, items(neg(neg(e.v(cols[0])))), opt.ColSet{})
	want := e.f.ConstructProject(s, items(e.v(cols[0])), opt.ColSet{})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructProject(s, items(neg(e.v(cols[0]))), opt.ColSet{}))
	e.expectNoMatch(rule, e.f.ConstructProject(s, items(neg(e.f.ConstructPlus(e.v(cols[0]), e.num(1)))), opt.ColSet{}))
}
