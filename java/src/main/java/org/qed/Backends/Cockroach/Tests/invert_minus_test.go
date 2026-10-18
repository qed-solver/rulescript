package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestInvertMinus(t *testing.T) {
	rule := loadRule(t, "InvertMinus")
	e := newEnv(t)
	s, c := e.scan("t", 2)
	out := e.f.Metadata().AddColumn("neg", types.Int)
	neg := e.f.ConstructUnaryMinus(e.f.ConstructMinus(e.v(c[0]), e.v(c[1])))
	before := e.f.ConstructProject(s, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(neg, out)}, opt.ColSet{})
	want := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructMinus(e.v(c[1]), e.v(c[0])), out),
	}, opt.ColSet{})
	e.expectRewriteUpToFreshColumns(rule, before, want)

	plus := e.f.ConstructUnaryMinus(e.f.ConstructPlus(e.v(c[0]), e.v(c[1])))
	e.expectNoMatch(rule, e.f.ConstructProject(s, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(plus, out)}, opt.ColSet{}))

	e.expectNoMatch(rule, e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructMinus(e.v(c[0]), e.v(c[1])), out),
	}, opt.ColSet{}))

	e.expectNoMatch(rule, e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructUnaryMinus(e.v(c[0])), out),
	}, opt.ColSet{}))

	wide, wc := e.scan("t", 3)
	wideNeg := e.f.ConstructUnaryMinus(e.f.ConstructMinus(e.v(wc[0]), e.v(wc[1])))
	e.expectNoMatch(rule, e.f.ConstructProject(wide, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(wideNeg, out)}, opt.ColSet{}))
}
