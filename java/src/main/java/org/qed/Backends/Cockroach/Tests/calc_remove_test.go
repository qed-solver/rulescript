package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestCalcRemove(t *testing.T) {
	rule := loadRule(t, "CalcRemove")
	e := newEnv(t)

	scan, sc := e.scan("t", 2)
	identity := e.f.ConstructProject(scan, nil, opt.MakeColSet(sc...))
	e.expectRewrite(rule, identity, scan)

	constant := e.f.Metadata().AddColumn("k", types.Int)
	e.expectNoMatch(rule, e.f.ConstructProject(scan, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.num(1), constant),
	}, opt.MakeColSet(sc[0])))

	wide, wc := e.scan("t", 3)
	e.expectNoMatch(rule, e.f.ConstructProject(wide, nil, opt.MakeColSet(wc...)))
}
