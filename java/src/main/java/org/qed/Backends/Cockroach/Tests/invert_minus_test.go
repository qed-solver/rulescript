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
	s, cols := e.scan("t", 2)
	z := e.f.Metadata().AddColumn("z", types.Int)
	project := func(x opt.ScalarExpr) memo.RelExpr {
		return e.f.ConstructProject(s, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(x, z)}, opt.ColSet{})
	}
	a, b := e.v(cols[0]), e.v(cols[1])
	e.expectRewrite(rule, project(e.f.ConstructUnaryMinus(e.f.ConstructMinus(a, b))),
		project(e.f.ConstructMinus(b, a)))
	e.expectNoMatch(rule, project(e.f.ConstructMinus(a, b)))
	e.expectNoMatch(rule, project(e.f.ConstructUnaryMinus(e.f.ConstructPlus(a, b))))
}
