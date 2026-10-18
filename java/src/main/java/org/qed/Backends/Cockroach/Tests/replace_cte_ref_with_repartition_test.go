package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestReplaceCTERefWithRepartition(t *testing.T) {
	rule := loadRule(t, "ReplaceCTERefWithRepartition")
	e := newEnv(t)

	before, cols := e.scan("t", 1)
	want := e.f.ConstructProject(before, memo.EmptyProjectionsExpr, opt.MakeColSet(cols...))
	e.expectRewrite(rule, before, want)

	nearMiss, _ := e.scan("t", 2)
	e.expectNoMatch(rule, nearMiss)
}
